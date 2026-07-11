package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：复制槽（分钟级，pg_replication_slots）。
 * <ul>
 *   <li>pg.repl.slots_total / pg.repl.slots_inactive：复制槽总数与失活槽数；</li>
 *   <li>pg.repl.slot_retained_bytes_max：单槽滞留 WAL 字节数最大值。
 *       失活槽会阻止 WAL 回收，是 PG 磁盘被 WAL 撑爆的最常见原因之一。</li>
 * </ul>
 * 主备均可查询；从库上以回放位点为当前位点计算滞留量。
 */
@Component
public class PgReplicationSlotsItem implements PgMetricItem {

    public static final String CODE = "pg_repl_slots";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT COUNT(*)                                                        AS slots_total,
                           COUNT(*) FILTER (WHERE NOT active)                              AS slots_inactive,
                           COALESCE(MAX(pg_wal_lsn_diff(
                               CASE WHEN pg_is_in_recovery() THEN pg_last_wal_replay_lsn()
                                    ELSE pg_current_wal_lsn() END, restart_lsn)), 0)       AS retained_bytes_max
                      FROM pg_replication_slots
                     WHERE restart_lsn IS NOT NULL
                    """)) {
                if (rs.next()) {
                    double total = rs.getDouble("slots_total");
                    sink.addNumeric("pg.repl.slots_total", total, ts);
                    if (total > 0) {
                        sink.addNumeric("pg.repl.slots_inactive", rs.getDouble("slots_inactive"), ts);
                        sink.addNumeric("pg.repl.slot_retained_bytes_max",
                                Math.max(0, rs.getDouble("retained_bytes_max")), ts);
                    }
                }
            }
        }
    }
}
