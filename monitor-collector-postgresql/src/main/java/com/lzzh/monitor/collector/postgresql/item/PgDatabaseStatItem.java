package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：库级吞吐与缓存（分钟级，pg_stat_database 累积计数器差值）。
 * <ul>
 *   <li>TPS：xact_commit + xact_rollback 的每秒速率（对标 mysql.tps）；</li>
 *   <li>缓存命中率：本周期 blks_hit / (blks_hit + blks_read)（对标 Buffer Pool 命中率）；</li>
 *   <li>元组读写速率：tup_fetched / inserted / updated / deleted；</li>
 *   <li>临时文件：每分钟新增落盘临时文件数与字节速率（对标磁盘临时表）；</li>
 *   <li>死锁：每分钟新增死锁次数。</li>
 * </ul>
 * 首个采集周期无基线，速率类指标跳过（下一周期恢复）。
 */
@Component
public class PgDatabaseStatItem implements PgMetricItem {

    public static final String CODE = "database_stat";

    @Resource
    private PgCounterDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        long instanceId = request.getInstanceId();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.databaseStatSql())) {
                if (!rs.next()) {
                    return;
                }
                Double resetAge = PgStatsResetSupport.ageSeconds(rs, "stats_reset", ts);
                if (resetAge != null) {
                    sink.addNumeric("pg.stats.database_reset_age_seconds", resetAge, ts);
                }
                long commit = rs.getLong("xact_commit");
                long rollback = rs.getLong("xact_rollback");
                Double commitRate = deltaStore.rate(instanceId, "xact_commit", commit, ts);
                Double rollbackRate = deltaStore.rate(instanceId, "xact_rollback", rollback, ts);
                if (commitRate != null && rollbackRate != null) {
                    sink.addNumeric("pg.tps", round2(commitRate + rollbackRate), ts);
                    sink.addNumeric("pg.rate.xact_commit", round2(commitRate), ts);
                    sink.addNumeric("pg.rate.xact_rollback", round2(rollbackRate), ts);
                }

                Long hitDelta = deltaStore.delta(instanceId, "blks_hit", rs.getLong("blks_hit"), ts);
                Long readDelta = deltaStore.delta(instanceId, "blks_read", rs.getLong("blks_read"), ts);
                if (hitDelta != null && readDelta != null && hitDelta + readDelta > 0) {
                    sink.addNumeric("pg.cache.hit_rate",
                            Math.round(hitDelta * 10000.0 / (hitDelta + readDelta)) / 100.0, ts);
                }

                rate(sink, instanceId, "tup_fetched", rs.getLong("tup_fetched"), "pg.rate.tup_fetched", ts);
                rate(sink, instanceId, "tup_inserted", rs.getLong("tup_inserted"), "pg.rate.tup_inserted", ts);
                rate(sink, instanceId, "tup_updated", rs.getLong("tup_updated"), "pg.rate.tup_updated", ts);
                rate(sink, instanceId, "tup_deleted", rs.getLong("tup_deleted"), "pg.rate.tup_deleted", ts);

                Long tempFiles = deltaStore.delta(instanceId, "temp_files", rs.getLong("temp_files"), ts);
                if (tempFiles != null) {
                    sink.addNumeric("pg.delta.temp_files", tempFiles, ts);
                }
                rate(sink, instanceId, "temp_bytes", rs.getLong("temp_bytes"), "pg.rate.temp_bytes", ts);

                Long deadlocks = deltaStore.delta(instanceId, "deadlocks", rs.getLong("deadlocks"), ts);
                if (deadlocks != null) {
                    sink.addNumeric("pg.delta.deadlocks", deadlocks, ts);
                }
            }
        }
    }

    private void rate(PgMetricSink sink, long instanceId, String counter, long value, String metric, long ts) {
        Double r = deltaStore.rate(instanceId, counter, value, ts);
        if (r != null) {
            sink.addNumeric(metric, round2(r), ts);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
