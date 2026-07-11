package com.lzzh.monitor.service.retention;

import com.lzzh.monitor.dao.retention.TimescaleRetentionPolicyDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 保留策略下发器：把 retention_config 的天数应用到 TimescaleDB Hypertable 的保留策略（§12.2）。
 * <p>分钟/小时/天/慢SQL样本四类分别映射到对应 Hypertable；
 * 每次调用先 remove_retention_policy 再按当前天数 add_retention_policy，实现「配置即生效」。
 * enabled=false 时仅移除策略（数据不再自动清理，长期保留）。
 * <p>event/log/report 为普通表/文件，不走 TimescaleDB 保留策略，由 {@code RetentionCleanupJob} 定时清理。
 */
@Component
public class RetentionPolicyApplier {

    private static final Logger log = LoggerFactory.getLogger(RetentionPolicyApplier.class);

    /** 保留类别 → 受该类别天数控制的 Hypertable 列表。 */
    private static final Map<String, List<String>> HYPERTABLES = Map.of(
            "minute", List.of("metric_data_1m", "metric_text_data_1m"),
            "hourly", List.of("metric_data_1h", "metric_text_data_1h", "metric_top_sql", "metric_capacity_object"),
            "daily", List.of("metric_data_1d", "metric_text_data_1d"),
            "slow_sql_sample", List.of("metric_slow_sql_sample"));

    /**
     * 保留类别 → 受该类别天数控制的连续聚合（cagg）列表。
     * <p>cagg 背后是独立的物化超表，保留策略需要单独下发（V22/V51 建时给过默认值，
     * 此处纳入统一管理，保证"数据保留"页面配置对 cagg 同样生效）：
     * <ul>
     *   <li>metric_data_1h_cagg：1m → 1h 降采样，历史性能分析的小时级视图数据源，随 hourly 走；</li>
     *   <li>metric_data_1d_cagg / capacity_object_daily：天级聚合，随 daily 走
     *       （capacity_instance_daily 自 V148 起为普通视图，无需保留策略）。</li>
     * </ul>
     */
    private static final Map<String, List<String>> CAGGS = Map.of(
            "hourly", List.of("metric_data_1h_cagg"),
            "daily", List.of("metric_data_1d_cagg", "capacity_object_daily"));

    private final TimescaleRetentionPolicyDao policyDao;

    public RetentionPolicyApplier(TimescaleRetentionPolicyDao policyDao) {
        this.policyDao = policyDao;
    }

    /** 该类别是否由 TimescaleDB 保留策略承载（分钟/小时/天/慢SQL样本）。 */
    public static boolean isHypertableCategory(String category) {
        return HYPERTABLES.containsKey(category);
    }

    /**
     * 将某类别的保留天数下发到对应 Hypertable。
     *
     * @param category      保留类别（minute/hourly/daily/slow_sql_sample）
     * @param retentionDays 保留天数（&gt;0 生效）
     * @param enabled       是否启用；false 则仅移除策略
     */
    public void apply(String category, Integer retentionDays, boolean enabled) {
        List<String> tables = HYPERTABLES.get(category);
        if (tables == null) {
            return;
        }
        if (!policyDao.timescaleEnabled()) {
            log.debug("未启用 TimescaleDB 扩展，跳过保留策略下发 category={}", category);
            return;
        }
        int days = retentionDays == null ? 0 : retentionDays;
        for (String table : tables) {
            if (policyDao.hypertableExists(table)) {
                applyToTarget(table, category, days, enabled);
            }
        }
        for (String cagg : CAGGS.getOrDefault(category, List.of())) {
            // 纯 PG 环境下 capacity_object_daily 是普通物化视图，无法设保留策略，跳过
            if (policyDao.continuousAggregateExists(cagg)) {
                applyToTarget(cagg, category, days, enabled);
            }
        }
    }

    /** 对单个超表 / 连续聚合下发保留策略（add/remove_retention_policy 两者通用）。 */
    private void applyToTarget(String target, String category, int days, boolean enabled) {
        try {
            policyDao.removeRetentionPolicy(target);
            if (enabled && days > 0) {
                policyDao.addRetentionPolicy(target, days);
            }
            log.info("保留策略已下发 target={} days={} enabled={}", target, days, enabled);
        } catch (Exception e) {
            log.warn("保留策略下发失败 target={} category={}: {}", target, category, e.getMessage());
        }
    }
}
