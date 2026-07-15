package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.PgQueryStatPoint;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** pg_stat_statements 小时级差值采集，同时兼容原 Top SQL 榜单与 PG Query Analytics。 */
@Component
public class PgTopSqlItem implements PgMetricItem {

    public static final String CODE = "pg_top_sql";
    private static final int TOP_N = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 20;
    private static final int MAX_QUERY_TEXT_LEN = 4000;
    private static final double MS_TO_PS = 1_000_000_000d;

    private static final String[] COUNTERS = {
            "plans", "total_plan_time",
            "shared_blks_hit", "shared_blks_read", "shared_blks_dirtied", "shared_blks_written",
            "local_blks_hit", "local_blks_read", "local_blks_dirtied", "local_blks_written",
            "temp_blks_read", "temp_blks_written", "blk_read_time", "blk_write_time",
            "wal_records", "wal_fpi", "wal_bytes",
            "jit_functions", "jit_generation_time", "jit_inlining_count", "jit_inlining_time",
            "jit_optimization_count", "jit_optimization_time",
            "jit_emission_count", "jit_emission_time"
    };

    @Resource
    private PgTopSqlDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        String extensionSchema = extensionSchema(conn);
        if (extensionSchema == null) return;
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();
        String sql = """
                SELECT d.datname, r.rolname, s.queryid::text AS queryid, s.query,
                       s.calls, s.total_exec_time, s.rows,
                       s.plans, s.total_plan_time, s.min_plan_time, s.max_plan_time,
                       s.mean_plan_time, s.stddev_plan_time,
                       s.min_exec_time, s.max_exec_time, s.mean_exec_time, s.stddev_exec_time,
                       s.shared_blks_hit, s.shared_blks_read, s.shared_blks_dirtied, s.shared_blks_written,
                       s.local_blks_hit, s.local_blks_read, s.local_blks_dirtied, s.local_blks_written,
                       s.temp_blks_read, s.temp_blks_written, s.blk_read_time, s.blk_write_time,
                       s.wal_records, s.wal_fpi, s.wal_bytes,
                       s.jit_functions, s.jit_generation_time, s.jit_inlining_count, s.jit_inlining_time,
                       s.jit_optimization_count, s.jit_optimization_time,
                       s.jit_emission_count, s.jit_emission_time,
                       i.stats_reset::text AS stats_reset, i.dealloc,
                       (current_setting('track_io_timing') = 'on') AS io_timing_enabled
                  FROM %s.pg_stat_statements s
                  LEFT JOIN pg_database d ON d.oid = s.dbid
                  LEFT JOIN pg_roles r ON r.oid = s.userid
                  CROSS JOIN %s.pg_stat_statements_info i
                 WHERE s.queryid IS NOT NULL
                 ORDER BY s.total_exec_time DESC
                 LIMIT %d
                """.formatted(extensionSchema, extensionSchema, TOP_N);

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    collectRow(instanceId, ts, rs, sink);
                }
            }
        }
    }

    private void collectRow(long instanceId, long ts, ResultSet rs, PgMetricSink sink) throws SQLException {
        String database = rs.getString("datname");
        String user = rs.getString("rolname");
        String queryId = rs.getString("queryid");
        String queryText = truncate(rs.getString("query"), MAX_QUERY_TEXT_LEN);
        long calls = rs.getLong("calls");
        double totalExecMs = rs.getDouble("total_exec_time");
        long rows = rs.getLong("rows");
        long sharedRead = rs.getLong("shared_blks_read");
        long sharedHit = rs.getLong("shared_blks_hit");
        long tempWritten = rs.getLong("temp_blks_written");
        String statsReset = rs.getString("stats_reset");
        long dealloc = rs.getLong("dealloc");

        PgTopSqlDeltaStore.Delta legacy = deltaStore.compute(instanceId, database, queryId,
                calls, totalExecMs, rows, sharedRead, sharedHit, tempWritten);
        TopSqlPoint topSql = legacy == null
                ? new TopSqlPoint(database, queryId, queryText, calls,
                    (long) (totalExecMs * MS_TO_PS), sharedRead, rows, ts)
                : new TopSqlPoint(database, queryId, queryText, calls,
                    (long) (totalExecMs * MS_TO_PS), sharedRead, rows,
                    legacy.deltaCalls(), (long) (legacy.deltaExecMs() * MS_TO_PS),
                    (long) (legacy.deltaExecMs() * 1000 / legacy.deltaCalls()),
                    legacy.deltaSharedRead(), legacy.deltaRows(),
                    0L, 0L, 0L, 0L, legacy.deltaTempWritten(), ts);
        sink.addTopSql(topSql);

        Map<String, Double> counters = new LinkedHashMap<>();
        for (String name : COUNTERS) counters.put(name, rs.getDouble(name));
        PgTopSqlDeltaStore.ExtendedDelta delta = deltaStore.computeExtended(
                instanceId, database, queryId, calls, totalExecMs, rows, counters, statsReset);
        if (delta == null) return;

        Map<String, Double> metrics = delta.metrics();
        metrics.put("min_plan_time_ms", rs.getDouble("min_plan_time"));
        metrics.put("max_plan_time_ms", rs.getDouble("max_plan_time"));
        metrics.put("mean_plan_time_ms", rs.getDouble("mean_plan_time"));
        metrics.put("stddev_plan_time_ms", rs.getDouble("stddev_plan_time"));
        metrics.put("min_exec_time_ms", rs.getDouble("min_exec_time"));
        metrics.put("max_exec_time_ms", rs.getDouble("max_exec_time"));
        metrics.put("mean_exec_time_ms", rs.getDouble("mean_exec_time"));
        metrics.put("stddev_exec_time_ms", rs.getDouble("stddev_exec_time"));
        metrics.put("io_timing_enabled", rs.getBoolean("io_timing_enabled") ? 1d : 0d);
        sink.addPgQueryStat(new PgQueryStatPoint(database, user, queryId, queryText,
                delta.deltaCalls(), delta.deltaExecMs(), delta.deltaRows(),
                metrics, statsReset, dealloc, ts));
    }

    private static String extensionSchema(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery("""
                    SELECT quote_ident(n.nspname)
                      FROM pg_extension e
                      JOIN pg_namespace n ON n.oid=e.extnamespace
                     WHERE e.extname='pg_stat_statements'
                    """)) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }
    private static String truncate(String text, int max) {
        return text != null && text.length() > max ? text.substring(0, max) : text;
    }
}