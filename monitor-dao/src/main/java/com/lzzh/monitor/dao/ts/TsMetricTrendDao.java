package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsMetricTrendMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 时序指标趋势查询 DAO。
 * <p>分钟级（{@code 1m}）直查 {@code metric_data_1m}；小时级（{@code 1h}）按"只采分钟级一份、
 * 小时级视图由 1m 降采样而来"的策略取数：
 * <ol>
 *   <li>天生按小时采集的指标（容量/binlog/错误日志）直查 {@code metric_data_1h} 原始表；</li>
 *   <li>分钟级指标查 TimescaleDB 连续聚合 {@code metric_data_1h_cagg}（保留 180 天），
 *       并用 1m 现算补齐 cagg 刷新滞后（end_offset 1 小时）的尾部窗口；</li>
 *   <li>无 TimescaleDB 的纯 PG 环境（cagg 不存在）整段回退为 1m 现算降采样。</li>
 * </ol>
 */
@Repository
public class TsMetricTrendDao {

    private static final Logger log = LoggerFactory.getLogger(TsMetricTrendDao.class);

    private static final long HOUR_MILLIS = 3600_000L;

    private final TsMetricTrendMapper mapper;

    /** cagg 视图存在性缓存（懒加载一次；迁移在应用启动前完成，运行期不会变化）。 */
    private volatile Boolean caggAvailable;

    public TsMetricTrendDao(TsMetricTrendMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询指定指标在时间范围内的趋势点列表（分钟级）。
     */
    public List<TrendPoint> queryTrend(Long instanceId, String metricCode, long from, long to) {
        return queryTrendByFrequency(instanceId, metricCode, from, to, "1m");
    }

    /**
     * 查询指定指标在时间范围内的趋势点列表。
     *
     * @param instanceId 实例 ID
     * @param metricCode 指标编码（如 mysql.qps）
     * @param from       开始时间（毫秒时间戳，含）
     * @param to         结束时间（毫秒时间戳，含）
     * @param frequency  数据频率：{@code "1m"} 分钟级 / {@code "1h"} 小时级
     * @return 按时间升序排列的趋势点列表，最多 2000 条
     */
    public List<TrendPoint> queryTrendByFrequency(Long instanceId, String metricCode,
                                                   long from, long to, String frequency) {
        if (!"1h".equals(frequency)) {
            return toPoints(mapper.selectTrend1m(instanceId, metricCode, new Timestamp(from), new Timestamp(to)));
        }
        return queryHourly(instanceId, metricCode, from, to);
    }

    private List<TrendPoint> queryHourly(Long instanceId, String metricCode, long from, long to) {
        Timestamp fromTs = new Timestamp(from);
        Timestamp toTs = new Timestamp(to);

        // 天生小时级指标（写原始 1h 表，与 1m 指标集合不相交）：直查即可
        List<TrendPoint> nativeHourly = toPoints(mapper.selectTrend1h(instanceId, metricCode, fromTs, toTs));
        if (!nativeHourly.isEmpty()) {
            return nativeHourly;
        }

        // 分钟级指标：cagg 降采样 + 1m 现算补尾；无 cagg 环境整段现算
        if (!isCaggAvailable()) {
            return toPoints(mapper.selectTrend1hAggFrom1m(instanceId, metricCode, fromTs, toTs));
        }
        List<TrendPoint> cagg = toPoints(mapper.selectTrend1hCagg(instanceId, metricCode, fromTs, toTs));
        if (cagg.isEmpty()) {
            // cagg 无覆盖（视图刚部署、或该指标无历史物化数据）：整段 1m 现算
            return toPoints(mapper.selectTrend1hAggFrom1m(instanceId, metricCode, fromTs, toTs));
        }
        List<TrendPoint> points = new ArrayList<>();
        // 头部补齐：cagg 策略只向前滚动物化（start_offset 3h），部署前的历史桶不会被回填，
        // 查询范围早于 cagg 覆盖起点时，用 1m 现算补齐头部（受限于 1m 表 30 天保留期，能补多少补多少）
        long firstBucket = cagg.get(0).ts();
        if (from < firstBucket) {
            points.addAll(toPoints(mapper.selectTrend1hAggFrom1m(
                    instanceId, metricCode, fromTs, new Timestamp(firstBucket - 1))));
        }
        points.addAll(cagg);
        // 尾部补齐：cagg 刷新滞后约 1 小时（end_offset 1h、每小时刷新一次），
        // 查询范围包含最近数小时时，尾部桶用 1m 现算补齐，避免图表右端出现空窗
        long tailFrom = cagg.get(cagg.size() - 1).ts() + HOUR_MILLIS;
        if (tailFrom <= to) {
            points.addAll(toPoints(mapper.selectTrend1hAggFrom1m(
                    instanceId, metricCode, new Timestamp(tailFrom), toTs)));
        }
        return points;
    }

    private boolean isCaggAvailable() {
        Boolean available = caggAvailable;
        if (available == null) {
            try {
                available = mapper.caggExists();
            } catch (Exception e) {
                log.warn("检测连续聚合视图失败，按不存在处理（回退 1m 现算降采样）：{}", e.getMessage());
                available = false;
            }
            caggAvailable = available;
        }
        return available;
    }

    private static List<TrendPoint> toPoints(List<Map<String, Object>> rows) {
        List<TrendPoint> points = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Timestamp ts = (Timestamp) row.get("collect_time");
            Number value = (Number) row.get("value");
            if (ts != null && value != null) {
                points.add(new TrendPoint(ts.toInstant().toEpochMilli(), value.doubleValue()));
            }
        }
        return points;
    }

    /** 趋势单点：时间戳（毫秒）+ 指标值。 */
    public record TrendPoint(long ts, double value) {}
}
