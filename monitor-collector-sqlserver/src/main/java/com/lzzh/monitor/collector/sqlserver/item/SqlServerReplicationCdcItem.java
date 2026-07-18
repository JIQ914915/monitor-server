package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 复制角色汇总、复制性能计数器延迟及 CDC 日志扫描延迟。 */
@Component
public class SqlServerReplicationCdcItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "replication_cdc";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        collectSummary(conn, adapter, sink, ts);
        collectReplicationLatency(conn, adapter, sink, ts);
        collectCdcLatency(conn, adapter, sink, ts);
    }

    private static void collectSummary(Connection conn, SqlServerVersionAdapter adapter,
                                       SqlServerMetricSink sink, long ts) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(adapter.replicationCdcSql())) {
            if (!rs.next()) return;
            sink.addNumeric("sqlserver.replication.published_databases", rs.getDouble("published_databases"), ts);
            sink.addNumeric("sqlserver.replication.subscribed_databases", rs.getDouble("subscribed_databases"), ts);
            sink.addNumeric("sqlserver.cdc.enabled_databases", rs.getDouble("cdc_databases"), ts);
        }
    }

    private static void collectReplicationLatency(Connection conn, SqlServerVersionAdapter adapter,
                                                  SqlServerMetricSink sink, long ts) throws Exception {
        double maxLatency = 0;
        int agents = 0;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(adapter.replicationLatencySql())) {
            while (rs.next()) {
                agents++;
                double latency = rs.getDouble("cntr_value");
                maxLatency = Math.max(maxLatency, latency);
                String agent = rs.getString("instance_name");
                sink.addObject("sqlserver.replication.delivery_latency_ms", "replication_agent", agent, latency, ts);
            }
        }
        if (agents > 0) sink.addNumeric("sqlserver.replication.max_delivery_latency_ms", maxLatency, ts);
    }

    private static void collectCdcLatency(Connection conn, SqlServerVersionAdapter adapter,
                                          SqlServerMetricSink sink, long ts) throws Exception {
        List<String> databases = cdcDatabases(conn);
        if (databases.isEmpty()) return;
        String originalDatabase = currentDatabase(conn);
        double maxLatency = 0;
        int captureInstances = 0;
        try {
            for (String database : databases) {
                try {
                    useDatabase(conn, database);
                    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(adapter.cdcLatencySql())) {
                        if (!rs.next()) continue;
                        captureInstances += rs.getInt("capture_instance_count");
                        double latency = rs.getDouble("cdc_latency_seconds");
                        if (!rs.wasNull()) {
                            maxLatency = Math.max(maxLatency, latency);
                            sink.addObject("sqlserver.cdc.scan_latency_seconds", "database", database, latency, ts);
                        }
                    }
                } catch (SQLException e) {
                    sink.addItemError("replication_cdc", database + ": " + e.getMessage());
                }
            }
        } finally {
            useDatabase(conn, originalDatabase);
        }
        sink.addNumeric("sqlserver.cdc.capture_instance_count", captureInstances, ts);
        sink.addNumeric("sqlserver.cdc.max_scan_latency_seconds", maxLatency, ts);
    }

    private static List<String> cdcDatabases(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT name FROM sys.databases
                 WHERE database_id>4 AND state_desc='ONLINE' AND is_cdc_enabled=1
                   AND HAS_DBACCESS(name)=1 ORDER BY name
                """)) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    private static String currentDatabase(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT DB_NAME()")) {
            if (!rs.next()) throw new SQLException("无法读取当前数据库上下文");
            return rs.getString(1);
        }
    }

    private static void useDatabase(Connection conn, String database) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("USE [" + database.replace("]", "]]" ) + "]");
        }
    }
}