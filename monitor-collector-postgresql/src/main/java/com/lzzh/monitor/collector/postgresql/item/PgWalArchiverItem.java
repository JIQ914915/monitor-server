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
 * 采集项：WAL 产出与归档（分钟级）。
 * <ul>
 *   <li>pg.wal.write_rate：WAL 生成速率（字节/秒，PG 14+ 来自 pg_stat_wal.wal_bytes 差值；
 *       PG 13 无该视图，跳过）；</li>
 *   <li>pg.wal.archive_failed_delta：周期内归档失败次数（pg_stat_archiver 差值，全版本）；
 *       持续大于 0 说明归档命令故障，WAL 会持续堆积撑爆磁盘；</li>
 *   <li>pg.wal.archived_delta：周期内归档成功次数（未开 archive_mode 时恒为 0）。</li>
 * </ul>
 */
@Component
public class PgWalArchiverItem implements PgMetricItem {

    public static final String CODE = "pg_wal";

    @Resource
    private PgCounterDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            if (majorVersion(request.getVersion()) >= 14) {
                try (ResultSet rs = st.executeQuery("SELECT wal_bytes, stats_reset FROM pg_stat_wal")) {
                    if (rs.next()) {
                        Double resetAge = PgStatsResetSupport.ageSeconds(rs, "stats_reset", ts);
                        if (resetAge != null) {
                            sink.addNumeric("pg.stats.wal_reset_age_seconds", resetAge, ts);
                        }
                        Double rate = deltaStore.rate(instanceId, "pg.wal.write_rate",
                                rs.getBigDecimal("wal_bytes").longValue(), ts);
                        if (rate != null) {
                            sink.addNumeric("pg.wal.write_rate", rate, ts);
                        }
                    }
                }
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT archived_count, failed_count FROM pg_stat_archiver")) {
                if (rs.next()) {
                    Long archived = deltaStore.delta(instanceId, "pg.wal.archived_delta",
                            rs.getLong("archived_count"), ts);
                    Long failed = deltaStore.delta(instanceId, "pg.wal.archive_failed_delta",
                            rs.getLong("failed_count"), ts);
                    if (archived != null) {
                        sink.addNumeric("pg.wal.archived_delta", archived, ts);
                    }
                    if (failed != null) {
                        sink.addNumeric("pg.wal.archive_failed_delta", failed, ts);
                    }
                }
            }
        }
    }

    private static int majorVersion(String version) {
        if (version == null || version.isBlank()) {
            return 13;
        }
        try {
            String head = version.split("[^0-9]")[0];
            return head.isEmpty() ? 13 : Integer.parseInt(head);
        } catch (Exception e) {
            return 13;
        }
    }
}
