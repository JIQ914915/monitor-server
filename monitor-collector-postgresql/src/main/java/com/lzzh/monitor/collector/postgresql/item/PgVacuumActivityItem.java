package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：vacuum 活动（分钟级）。
 * <ul>
 *   <li>pg.vacuum.running：当前正在执行的 vacuum/autovacuum 数（pg_stat_progress_vacuum）；</li>
 *   <li>pg.vacuum.xmin_horizon_seconds：阻碍 vacuum 清理的最老事务持续秒数
 *       （长事务/悬挂事务会让死元组无法回收，膨胀持续累积）。</li>
 * </ul>
 */
@Component
public class PgVacuumActivityItem implements PgMetricItem {

    public static final String CODE = "pg_vacuum";

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
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) AS running FROM pg_stat_progress_vacuum")) {
                if (rs.next()) {
                    sink.addNumeric("pg.vacuum.running", rs.getDouble("running"), ts);
                }
            }
            try (ResultSet rs = st.executeQuery("""
                    SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (now() - xact_start)))
                                    FILTER (WHERE backend_xmin IS NOT NULL), 0)  AS xmin_horizon_seconds
                      FROM pg_stat_activity
                     WHERE backend_type = 'client backend'
                    """)) {
                if (rs.next()) {
                    sink.addNumeric("pg.vacuum.xmin_horizon_seconds", rs.getDouble("xmin_horizon_seconds"), ts);
                }
            }
        }
    }
}
