package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

/** Query Store 最近一小时 Top SQL；不可用时只读降级为 DMV 累计快照。 */
@Component
public class SqlServerTopSqlItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "top_sql";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, SqlServerVersionAdapter adapter,
                        SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        if (!adapter.supportsQueryStore()) {
            sink.addText("sqlserver.query_store.collect_state", "version_not_support", ts);
            collectRows(conn, adapter.dmvTopSql(), sink, ts);
            return;
        }
        try {
            if (!queryStoreEnabled(conn, adapter)) {
                sink.addText("sqlserver.query_store.collect_state", "not_enabled", ts);
                collectRows(conn, adapter.dmvTopSql(), sink, ts);
                return;
            }
            collectRows(conn, adapter.queryStoreTopSql(), sink, ts);
            sink.addText("sqlserver.query_store.collect_state", "available", ts);
        } catch (Exception queryStoreUnavailable) {
            sink.addText("sqlserver.query_store.collect_state",
                    permissionDenied(queryStoreUnavailable) ? "permission_denied" : "collect_error", ts);
            collectRows(conn, adapter.dmvTopSql(), sink, ts);
        }
    }

    private boolean queryStoreEnabled(Connection conn, SqlServerVersionAdapter adapter) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(10);
            try (ResultSet rs = st.executeQuery(adapter.queryStoreCapabilitySql())) {
                if (!rs.next()) return false;
                int state = rs.getInt("actual_state");
                return !rs.wasNull() && state != 0;
            }
        }
    }

    private void collectRows(Connection conn, String sql, SqlServerMetricSink sink, long ts) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(15);
            st.setMaxRows(50);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    long executions = Math.max(0, rs.getLong("executions"));
                    long durationUs = Math.max(0, rs.getLong("duration_us"));
                    long reads = Math.max(0, rs.getLong("logical_reads"));
                    long physicalReads = Math.max(0, rs.getLong("physical_reads"));
                    long rows = Math.max(0, rs.getLong("rows_count"));
                    long writes = Math.max(0, rs.getLong("writes"));
                    sink.addTopSql(new TopSqlPoint(rs.getString("database_name"), rs.getString("digest"),
                            SqlServerSqlRedactor.redact(rs.getString("sql_text")), executions,
                            durationUs * 1_000_000L, reads, rows, executions, durationUs * 1_000_000L,
                            executions == 0 ? 0 : durationUs / executions, reads, rows, 0L, 0L, 0L, 0L, 0L,
                            physicalReads, writes, ts));
                }
            }
        }
    }

    private static boolean permissionDenied(Exception e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return message.contains("permission") || message.contains("denied")
                || message.contains("not have permission");
    }
}