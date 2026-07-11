package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：表膨胀与死元组（小时级，pg_stat_user_tables 近似口径）。
 * <ul>
 *   <li>pg.bloat.dead_tup_total：当前库死元组总数；</li>
 *   <li>pg.bloat.dead_pct_max：单表死元组占比最大值（只统计活元组&gt;1000 的表，避免小表噪声）；</li>
 *   <li>pg.bloat.tables_over_20pct：死元组占比超 20% 的表数量；</li>
 *   <li>pg.bloat.top_tables：Top5 膨胀表 JSON 明细（表名/死元组/占比/最后 autovacuum 时间）。</li>
 * </ul>
 * 说明：n_dead_tup 为统计估算值，不做 pgstattuple 精确采样（需扩展且重查询），
 * 对"发现膨胀风险 → 引导人工处理"的定位足够。
 */
@Component
public class PgBloatVacuumItem implements PgMetricItem {

    public static final String CODE = "pg_bloat";

    private static final int QUERY_TIMEOUT_SECONDS = 30;

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
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT COALESCE(SUM(n_dead_tup), 0)                                        AS dead_total,
                           COALESCE(MAX(n_dead_tup * 100.0 / NULLIF(n_live_tup + n_dead_tup, 0))
                                    FILTER (WHERE n_live_tup > 1000), 0)                       AS dead_pct_max,
                           COUNT(*) FILTER (WHERE n_live_tup > 1000
                                              AND n_dead_tup * 100.0 / NULLIF(n_live_tup + n_dead_tup, 0) > 20)
                                                                                               AS tables_over_20pct
                      FROM pg_stat_user_tables
                    """)) {
                if (rs.next()) {
                    sink.addNumeric("pg.bloat.dead_tup_total", rs.getDouble("dead_total"), ts);
                    sink.addNumeric("pg.bloat.dead_pct_max", rs.getDouble("dead_pct_max"), ts);
                    sink.addNumeric("pg.bloat.tables_over_20pct", rs.getDouble("tables_over_20pct"), ts);
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT schemaname, relname, n_live_tup, n_dead_tup,
                           ROUND(n_dead_tup * 100.0 / NULLIF(n_live_tup + n_dead_tup, 0), 1)   AS dead_pct,
                           to_char(GREATEST(last_vacuum, last_autovacuum), 'YYYY-MM-DD HH24:MI') AS last_vacuum_at
                      FROM pg_stat_user_tables
                     WHERE n_live_tup > 1000 AND n_dead_tup > 0
                     ORDER BY n_dead_tup DESC
                     LIMIT 5
                    """)) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"table\":\"").append(escape(rs.getString("schemaname") + "." + rs.getString("relname")))
                        .append("\",\"liveTup\":").append(rs.getLong("n_live_tup"))
                        .append(",\"deadTup\":").append(rs.getLong("n_dead_tup"))
                        .append(",\"deadPct\":").append(rs.getDouble("dead_pct"))
                        .append(",\"lastVacuumAt\":\"").append(rs.getString("last_vacuum_at") == null ? "" : rs.getString("last_vacuum_at"))
                        .append("\"}");
                }
                json.append(']');
                if (!first) {
                    sink.addText("pg.bloat.top_tables", json.toString(), ts);
                }
            }
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
