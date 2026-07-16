package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;

/** 调度器、请求、会话、阻塞与事务概况。 */
@Component
public class SqlServerRuntimeItem implements SqlServerMetricItem {
    private static final Map<String, String> COLUMNS = Map.of(
            "sqlserver.scheduler.runnable_tasks", "runnable_tasks",
            "sqlserver.scheduler.active_workers", "active_workers",
            "sqlserver.scheduler.current_tasks", "current_tasks",
            "sqlserver.session.user", "user_sessions",
            "sqlserver.request.active", "active_requests",
            "sqlserver.blocked_sessions", "blocked_sessions",
            "sqlserver.request.max_seconds", "max_request_seconds",
            "sqlserver.transaction.max_open_count", "max_open_transactions");

    @Override public String code() { return "runtime"; }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.runtimeSql())) {
                if (!rs.next()) return;
                for (Map.Entry<String, String> entry : COLUMNS.entrySet()) {
                    sink.addNumeric(entry.getKey(), rs.getDouble(entry.getValue()), ts);
                }
            }
        }
    }
}
