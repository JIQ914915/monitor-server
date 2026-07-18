package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Always On 本地及远程副本只读健康摘要；未启用时静默跳过，不执行故障转移。 */
@Component
public class SqlServerAlwaysOnItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "always_on";
    }

    @Override
    public void collect(Connection conn, CollectRequest request, SqlServerVersionAdapter adapter,
                        SqlServerMetricSink sink) throws Exception {
        if (!adapter.supportsAlwaysOn() || !enabled(conn)) return;
        long ts = System.currentTimeMillis();
        int disconnected = 0;
        int unhealthy = 0;
        int suspended = 0;
        double sendKb = 0;
        double redoKb = 0;
        double sendSec = 0;
        double redoSec = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(10);
            try (ResultSet rs = st.executeQuery(adapter.alwaysOnHealthSql())) {
                while (rs.next()) {
                    String name = objectName(rs);
                    String connected = rs.getString("connected_state_desc");
                    String health = rs.getString("synchronization_health_desc");
                    if (connected != null && !"CONNECTED".equals(connected)) disconnected++;
                    if (health != null && !"HEALTHY".equals(health)) unhealthy++;
                    if (rs.getBoolean("is_suspended")) suspended++;
                    Double queue = value(rs, "log_send_queue_size");
                    Double redoQueue = value(rs, "redo_queue_size");
                    Double estimatedSend = value(rs, "send_seconds");
                    Double estimatedRedo = value(rs, "redo_seconds");
                    if (queue != null) {
                        sendKb = Math.max(sendKb, queue);
                        sink.addObject("sqlserver.ag.log_send_queue_kb", "availability_database", name, queue, ts);
                    }
                    if (redoQueue != null) {
                        redoKb = Math.max(redoKb, redoQueue);
                        sink.addObject("sqlserver.ag.redo_queue_kb", "availability_database", name, redoQueue, ts);
                    }
                    if (estimatedSend != null) sendSec = Math.max(sendSec, estimatedSend);
                    if (estimatedRedo != null) redoSec = Math.max(redoSec, estimatedRedo);
                    sink.addObject("sqlserver.ag.role_code", "availability_database", name,
                            roleCode(rs.getString("role_desc")), ts);
                    sink.addObject("sqlserver.ag.is_local", "availability_database", name,
                            rs.getBoolean("is_local") ? 1 : 0, ts);
                }
            }
        }
        sink.addNumeric("sqlserver.ag.disconnected_replicas", disconnected, ts);
        sink.addNumeric("sqlserver.ag.unhealthy_databases", unhealthy, ts);
        sink.addNumeric("sqlserver.ag.suspended_databases", suspended, ts);
        sink.addNumeric("sqlserver.ag.max_log_send_queue_kb", sendKb, ts);
        sink.addNumeric("sqlserver.ag.max_redo_queue_kb", redoKb, ts);
        sink.addNumeric("sqlserver.ag.max_send_seconds", sendSec, ts);
        sink.addNumeric("sqlserver.ag.max_redo_seconds", redoSec, ts);
    }

    private static boolean enabled(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT CAST(SERVERPROPERTY('IsHadrEnabled') AS int)")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    private static String objectName(ResultSet rs) throws SQLException {
        return safe(rs.getString("group_name")) + "/" + safe(rs.getString("replica_server_name"))
                + "/" + safe(rs.getString("database_name"));
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static Double value(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static int roleCode(String role) {
        return "PRIMARY".equals(role) ? 1 : "SECONDARY".equals(role) ? 2 : 0;
    }
}