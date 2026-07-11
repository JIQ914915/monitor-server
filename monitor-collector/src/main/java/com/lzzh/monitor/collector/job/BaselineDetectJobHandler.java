package com.lzzh.monitor.collector.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.enums.InstanceStatus;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.TsBaselineQueryMapper;
import com.lzzh.monitor.dao.ts.TsMetricWriter;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基线学习与异常检测任务（§10.3 / §15.4.6，小时级调度）。
 *
 * <p>"业务时段基线"取<b>历史同小时</b>：同一实例、同一指标，过去
 * {@value LOOKBACK_DAYS} 天中每天当前小时的小时均值序列，计算均值 μ 与标准差 σ；
 * 当前观测值取最近 {@value CURRENT_WINDOW_MINUTES} 分钟均值。
 *
 * <p>异常判定（两条同时满足，避免低基数噪声）：
 * <ul>
 *   <li>偏离幅度：|cur - μ| ≥ {@value DEVIATION_PCT_THRESHOLD}% × μ；</li>
 *   <li>统计显著：|cur - μ| ≥ {@value SIGMA_MULTIPLIER} × σ（σ 极小时取 μ 的 10% 兜底）。</li>
 * </ul>
 * 样本不足 {@value MIN_SAMPLES} 天、或基线均值过小（业务几乎无量，QPS μ < 1）时不判定。
 *
 * <p>产出（写 metric_data_1h，供趋势查询与内置布尔规则消费）：
 * <ul>
 *   <li>{@code mysql.baseline.qps_deviation_pct} / {@code mysql.baseline.conn_deviation_pct}
 *       —— 当前值相对基线的偏离百分比（正=偏高，负=偏低）；</li>
 *   <li>{@code mysql.baseline.qps_anomaly} / {@code mysql.baseline.conn_anomaly}
 *       —— 异常标记（1/0），配套内置规则告警。</li>
 * </ul>
 */
@Component
public class BaselineDetectJobHandler {

    private static final Logger log = LoggerFactory.getLogger(BaselineDetectJobHandler.class);

    static final int LOOKBACK_DAYS = 7;
    static final int MIN_SAMPLES = 4;
    static final int CURRENT_WINDOW_MINUTES = 60;
    static final double DEVIATION_PCT_THRESHOLD = 50.0;
    static final double SIGMA_MULTIPLIER = 3.0;

    /** 待检测指标：code → [偏离指标名, 异常指标名, 最小有效基线均值]。 */
    private record Target(String metricCode, String deviationMetric, String anomalyMetric, double minBaseline) {
    }

    /** 基线指标按数据库类型配置：MySQL 看 QPS，PG 无语句计数器（一期）以 TPS 代之。 */
    private static final Map<DbType, List<Target>> TARGETS_BY_TYPE = Map.of(
            DbType.MYSQL, List.of(
                    new Target("mysql.qps",
                            "mysql.baseline.qps_deviation_pct", "mysql.baseline.qps_anomaly", 1.0),
                    new Target("mysql.conn.total",
                            "mysql.baseline.conn_deviation_pct", "mysql.baseline.conn_anomaly", 5.0)),
            DbType.POSTGRESQL, List.of(
                    new Target("pg.tps",
                            "pg.baseline.tps_deviation_pct", "pg.baseline.tps_anomaly", 1.0),
                    new Target("pg.conn.total",
                            "pg.baseline.conn_deviation_pct", "pg.baseline.conn_anomaly", 5.0))
    );

    private final DbInstanceMapper dbInstanceMapper;
    private final DatabaseTypeMapper databaseTypeMapper;
    private final TsBaselineQueryMapper baselineQueryMapper;
    private final TsMetricWriter metricWriter;

    public BaselineDetectJobHandler(DbInstanceMapper dbInstanceMapper,
                                    DatabaseTypeMapper databaseTypeMapper,
                                    TsBaselineQueryMapper baselineQueryMapper,
                                    TsMetricWriter metricWriter) {
        this.dbInstanceMapper = dbInstanceMapper;
        this.databaseTypeMapper = databaseTypeMapper;
        this.baselineQueryMapper = baselineQueryMapper;
        this.metricWriter = metricWriter;
    }

    @XxlJob("baselineDetectJobHandler")
    public void detect() {
        List<DbInstance> instances = dbInstanceMapper.selectList(
                new LambdaQueryWrapper<DbInstance>().ne(DbInstance::getStatus, InstanceStatus.PAUSED));
        if (instances.isEmpty()) {
            XxlJobHelper.log("无实例需要基线检测");
            return;
        }
        int hourOfDay = LocalDateTime.now().getHour();
        XxlJobHelper.log("开始 {} 个实例的基线检测（当前小时 {}，回看 {} 天同小时）",
                instances.size(), hourOfDay, LOOKBACK_DAYS);

        // dbTypeId → DbType 映射（database_type 行数极少，一次全量加载）
        Map<Long, DbType> typeById = new HashMap<>();
        for (DatabaseType t : databaseTypeMapper.selectList(null)) {
            DbType parsed = DbType.of(t.getCode());
            if (parsed != null) {
                typeById.put(t.getId(), parsed);
            }
        }

        long ts = System.currentTimeMillis();
        int evaluated = 0, anomalies = 0, skipped = 0, unsupported = 0;
        List<TsMetricWriter.TsMetricPoint> points = new ArrayList<>();
        for (DbInstance ins : instances) {
            DbType dbType = typeById.get(ins.getDbTypeId());
            List<Target> targets = TARGETS_BY_TYPE.get(dbType);
            if (targets == null) {
                unsupported++;
                log.warn("实例 {} 的数据库类型 {} 未配置基线检测指标，已跳过", ins.getId(), dbType);
                continue;
            }
            for (Target target : targets) {
                try {
                    Verdict v = evaluate(ins.getId(), target, hourOfDay);
                    if (v == null) {
                        skipped++;
                        continue;
                    }
                    evaluated++;
                    if (v.anomaly()) {
                        anomalies++;
                    }
                    points.add(new TsMetricWriter.TsMetricPoint(
                            ins.getId(), target.deviationMetric(), round2(v.deviationPct()), ts));
                    points.add(new TsMetricWriter.TsMetricPoint(
                            ins.getId(), target.anomalyMetric(), v.anomaly() ? 1 : 0, ts));
                } catch (Exception e) {
                    log.error("实例 {} 指标 {} 基线检测失败", ins.getId(), target.metricCode(), e);
                }
            }
        }
        metricWriter.batchWrite(CollectFrequency.HOURLY, points);
        XxlJobHelper.log("基线检测完成：判定 {} 项，异常 {} 项，样本不足跳过 {} 项，未支持类型跳过 {} 个实例", evaluated, anomalies, skipped, unsupported);
    }

    private record Verdict(double deviationPct, boolean anomaly) {
    }

    /** 单实例单指标评估；样本不足 / 无当前值 / 基线过小时返回 null（不产点）。 */
    private Verdict evaluate(Long instanceId, Target target, int hourOfDay) {
        List<Map<String, Object>> rows = baselineQueryMapper.selectSameHourAvgs(
                instanceId, target.metricCode(), hourOfDay, LOOKBACK_DAYS);
        if (rows.size() < MIN_SAMPLES) {
            return null;
        }
        double[] samples = rows.stream()
                .mapToDouble(r -> ((Number) r.get("avg_value")).doubleValue())
                .toArray();
        double mean = mean(samples);
        if (mean < target.minBaseline()) {
            // 业务几乎无量：偏离百分比无意义，不判定
            return null;
        }
        Double current = baselineQueryMapper.selectRecentAvg(
                instanceId, target.metricCode(), CURRENT_WINDOW_MINUTES);
        if (current == null) {
            return null;
        }
        double std = std(samples, mean);
        // σ 兜底：历史每天同小时都几乎一样时 σ→0，任何波动都会显著，用 μ 的 10% 托底
        double effectiveStd = Math.max(std, mean * 0.10);
        double diff = current - mean;
        double deviationPct = diff / mean * 100.0;
        boolean anomaly = Math.abs(deviationPct) >= DEVIATION_PCT_THRESHOLD
                && Math.abs(diff) >= SIGMA_MULTIPLIER * effectiveStd;
        return new Verdict(deviationPct, anomaly);
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double std(double[] values, double mean) {
        double sq = 0;
        for (double v : values) {
            sq += (v - mean) * (v - mean);
        }
        return Math.sqrt(sq / values.length);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
