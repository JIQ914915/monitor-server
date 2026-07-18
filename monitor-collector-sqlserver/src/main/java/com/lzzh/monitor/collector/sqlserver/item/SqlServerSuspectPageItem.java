package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.SqlServerDiagnosticEventPoint;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/** msdb suspect_pages 增量与受影响数据库明细；只读取未修复的 I/O 一致性错误线索。 */
@Component
public class SqlServerSuspectPageItem implements SqlServerMetricItem {
    private final SqlServerCounterDeltaStore deltaStore;

    public SqlServerSuspectPageItem(SqlServerCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return "integrity_events";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        long totalErrors = 0;
        int pageCount = 0;
        Map<String, Long> databaseErrors = new LinkedHashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            st.setMaxRows(1000);
            try (ResultSet rs = st.executeQuery(adapter.suspectPagesSql())) {
                while (rs.next()) {
                    pageCount++;
                    String database = rs.getString("database_name");
                    int databaseId = rs.getInt("database_id");
                    int fileId = rs.getInt("file_id");
                    long pageId = rs.getLong("page_id");
                    int eventType = rs.getInt("event_type");
                    long errorCount = rs.getLong("error_count");
                    Timestamp updated = rs.getTimestamp("last_update_date");
                    totalErrors += errorCount;
                    String databaseKey = database == null ? "database_id=" + databaseId : database;
                    databaseErrors.merge(databaseKey, errorCount, Long::sum);
                    String evidence = "database=" + databaseKey + ";file_id=" + fileId
                            + ";page_id=" + pageId + ";event_type=" + eventType
                            + ";error_count=" + errorCount;
                    long eventTime = updated == null ? ts : updated.getTime();
                    sink.addDiagnosticEvent(new SqlServerDiagnosticEventPoint(
                            "suspect_page", database, "critical",
                            sha256(evidence + ";last_update=" + eventTime), evidence, true, eventTime));
                }
            }
        }
        sink.addNumeric("sqlserver.integrity.suspect_page_count", pageCount, ts);
        sink.addNumeric("sqlserver.integrity.suspect_page_error_count", totalErrors, ts);
        deltaStore.delta(request.getInstanceId(), "integrity.suspect_page_errors", totalErrors, ts)
                .ifPresent(value -> sink.addNumeric("sqlserver.integrity.suspect_page_new_count", value, ts));
        databaseErrors.forEach((database, errors) -> sink.addObject(
                "sqlserver.integrity.suspect_page_error_count", "database", database, errors, ts));
    }

    private static String sha256(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) result.append(String.format("%02x", b));
        return result.toString();
    }
}