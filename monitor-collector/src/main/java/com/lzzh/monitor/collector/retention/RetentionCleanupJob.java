package com.lzzh.monitor.collector.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lzzh.monitor.dao.entity.RetentionConfig;
import com.lzzh.monitor.dao.mapper.RetentionConfigMapper;
import com.lzzh.monitor.dao.retention.RetentionCleanupDao;
import jakarta.annotation.Resource;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 普通表保留清理任务（§12.2）：event/log 类别的表不是 TimescaleDB Hypertable，
 * 无法用 add_retention_policy，改由每日定时 DELETE 按 retention_config 天数清理。
 * <ul>
 *   <li>event → alert_event（按 created_at）、pg_operational_event（按 event_time）</li>
 *   <li>log   → sys_oper_log（按 oper_time）</li>
 * </ul>
 * report 为文件产物（对象存储/磁盘），不在本任务范围。
 * <p>由 xxl-job 调度（handler：{@code retentionCleanupJobHandler}），建议每日凌晨执行一次。
 */
@Component
public class RetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupJob.class);

    @Resource
    private RetentionConfigMapper retentionMapper;
    @Resource
    private RetentionCleanupDao retentionCleanupDao;

    /** 普通表保留清理（建议 cron：{@code 0 30 3 * * ?}，每日 03:30）。 */
    @XxlJob("retentionCleanupJobHandler")
    public void cleanup() {
        cleanupEventTable();
        cleanupLogTable();
    }

    private void cleanupEventTable() {
        cleanup("event", "alert_event", retentionCleanupDao::deleteAlertEventsOlderThanDays);
        cleanup("event", "pg_operational_event", retentionCleanupDao::deletePgOperationalEventsOlderThanDays);
        cleanup("event", "mysql_plan_history", retentionCleanupDao::deleteMySqlPlanHistoryOlderThanDays);
        cleanup("event", "mysql_security_snapshot", retentionCleanupDao::deleteMySqlSecuritySnapshotsOlderThanDays);
    }

    private void cleanupLogTable() {
        cleanup("log", "sys_oper_log", retentionCleanupDao::deleteOperLogsOlderThanDays);
    }

    private void cleanup(String category, String table, CleanupExecutor cleanupExecutor) {
        RetentionConfig cfg = retentionMapper.selectOne(
                new LambdaQueryWrapper<RetentionConfig>().eq(RetentionConfig::getCategory, category));
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())
                || cfg.getRetentionDays() == null || cfg.getRetentionDays() <= 0) {
            return;
        }
        try {
            int deleted = cleanupExecutor.execute(cfg.getRetentionDays());
            if (deleted > 0) {
                log.info("保留清理 table={} 删除 {} 行（保留 {} 天）", table, deleted, cfg.getRetentionDays());
            }
        } catch (Exception e) {
            log.warn("保留清理失败 table={}: {}", table, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface CleanupExecutor {
        int execute(int retentionDays);
    }
}
