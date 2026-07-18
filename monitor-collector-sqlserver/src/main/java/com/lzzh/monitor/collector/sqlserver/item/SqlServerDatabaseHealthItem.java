package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** 用户数据库状态、访问模式、恢复模式与只读状态。 */
@Component
public class SqlServerDatabaseHealthItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "database_health";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        int total = 0;
        int abnormal = 0;
        int offline = 0;
        int suspect = 0;
        int recoveryPending = 0;
        int emergency = 0;
        int readOnly = 0;
        StringBuilder snapshot = new StringBuilder();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.databaseHealthSql())) {
                while (rs.next()) {
                    total++;
                    String database = rs.getString("database_name");
                    int state = rs.getInt("state");
                    int userAccess = rs.getInt("user_access");
                    int recoveryModel = rs.getInt("recovery_model");
                    boolean databaseReadOnly = rs.getBoolean("is_read_only");
                    if (state == 3 || state == 4 || state == 5 || state == 6) abnormal++;
                    if (state == 6) offline++;
                    if (state == 4) suspect++;
                    if (state == 3) recoveryPending++;
                    if (state == 5) emergency++;
                    if (databaseReadOnly) readOnly++;
                    sink.addObject("sqlserver.database.state_code", "database", database, state, ts);
                    sink.addObject("sqlserver.database.user_access_code", "database", database, userAccess, ts);
                    sink.addObject("sqlserver.database.recovery_model_code", "database", database, recoveryModel, ts);
                    sink.addObject("sqlserver.database.read_only", "database", database, databaseReadOnly ? 1 : 0, ts);
                    snapshot.append(database).append('|')
                            .append(rs.getString("state_desc")).append('|')
                            .append(rs.getString("user_access_desc")).append('|')
                            .append(rs.getString("recovery_model_desc")).append('|')
                            .append(databaseReadOnly ? "READ_ONLY" : "READ_WRITE").append('\n');
                }
            }
        }
        sink.addNumeric("sqlserver.database.total_count", total, ts);
        sink.addNumeric("sqlserver.database.abnormal_count", abnormal, ts);
        sink.addNumeric("sqlserver.database.offline_count", offline, ts);
        sink.addNumeric("sqlserver.database.suspect_count", suspect, ts);
        sink.addNumeric("sqlserver.database.recovery_pending_count", recoveryPending, ts);
        sink.addNumeric("sqlserver.database.emergency_count", emergency, ts);
        sink.addNumeric("sqlserver.database.read_only_count", readOnly, ts);
        sink.addText("sqlserver.database.state_snapshot", snapshot.toString(), ts);
    }
}