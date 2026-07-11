package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：连接数概况（分钟级）。
 * <ul>
 *   <li>当前/活跃/空闲/事务中空闲/等锁连接数：pg_stat_activity（仅 client backend）；</li>
 *   <li>最大连接数：max_connections 减去 superuser_reserved_connections 之前的原始值；</li>
 *   <li>连接使用率与活跃连接占比：相对 max_connections 的百分比（采集侧算出便于告警下钻）。</li>
 * </ul>
 */
@Component
public class PgConnectionsItem implements PgMetricItem {

    public static final String CODE = "connections";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        double total = 0;
        double active = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.connectionsSql())) {
                if (rs.next()) {
                    total = rs.getDouble("total");
                    active = rs.getDouble("active");
                    sink.addNumeric("pg.conn.total", total, ts);
                    sink.addNumeric("pg.conn.active", active, ts);
                    sink.addNumeric("pg.conn.idle", rs.getDouble("idle"), ts);
                    sink.addNumeric("pg.conn.idle_in_trx", rs.getDouble("idle_in_trx"), ts);
                    sink.addNumeric("pg.conn.waiting", rs.getDouble("waiting"), ts);
                }
            }
        }
        double maxConn = readMaxConnections(conn);
        if (maxConn > 0) {
            sink.addNumeric("pg.conn.max", maxConn, ts);
            sink.addNumeric("pg.conn.usage", Math.round(total / maxConn * 10000.0) / 100.0, ts);
            sink.addNumeric("pg.conn.active_pct", Math.round(active / maxConn * 10000.0) / 100.0, ts);
        }
    }

    private double readMaxConnections(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SELECT current_setting('max_connections')::int AS m")) {
                if (rs.next()) {
                    return rs.getDouble("m");
                }
            }
        }
        return 0;
    }
}
