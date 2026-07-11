package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：连接数概况（§7.3 连接域，分钟级）。
 * <ul>
 *   <li>当前连接总数 + 活跃(非 Sleep)连接数：information_schema.processlist（采样值）；</li>
 *   <li>最大连接数：@@max_connections（采样值）；</li>
 *   <li>连接使用率：total 相对 max 的占比（百分比，采集侧顺带算出便于告警下钻）；</li>
 *   <li>活跃连接占比：active 相对 max 的占比（百分比，内置规则"活跃连接数百分比预警"的数据来源）。</li>
 * </ul>
 */
@Component
public class ConnectionsItem implements MySqlMetricItem {

    public static final String CODE = "connections";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        String sql = "SELECT "
                + "COUNT(*) AS total, "
                + "SUM(CASE WHEN command <> 'Sleep' THEN 1 ELSE 0 END) AS active "
                + "FROM information_schema.processlist";
        double total = 0;
        double active = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    total = rs.getDouble("total");
                    active = rs.getDouble("active");
                    sink.addNumeric("mysql.conn.total", total, ts);
                    sink.addNumeric("mysql.conn.active", active, ts);
                }
            }
        }
        double maxConn = readMaxConnections(conn);
        if (maxConn > 0) {
            sink.addNumeric("mysql.conn.max", maxConn, ts);
            double usage = Math.round(total / maxConn * 10000.0) / 100.0;
            sink.addNumeric("mysql.conn.usage", usage, ts);
            double activePct = Math.round(active / maxConn * 10000.0) / 100.0;
            sink.addNumeric("mysql.conn.active_pct", activePct, ts);
        }
    }

    private double readMaxConnections(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SELECT @@max_connections AS m")) {
                if (rs.next()) {
                    return rs.getDouble("m");
                }
            }
        }
        return 0;
    }
}
