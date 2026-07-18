package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** 活动事务及 sleeping 状态下仍未提交的打开事务。 */
@Component
public class SqlServerTransactionItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "transactions";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        int count = 0;
        int sleepingCount = 0;
        long maxSeconds = 0;
        long sleepingMaxSeconds = 0;
        StringBuilder snapshot = new StringBuilder();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            st.setMaxRows(500);
            try (ResultSet rs = st.executeQuery(adapter.transactionDetailSql())) {
                while (rs.next()) {
                    count++;
                    int sessionId = rs.getInt("session_id");
                    long seconds = Math.max(0, rs.getLong("transaction_seconds"));
                    boolean sleeping = rs.getInt("sleeping_open") == 1;
                    maxSeconds = Math.max(maxSeconds, seconds);
                    if (sleeping) {
                        sleepingCount++;
                        sleepingMaxSeconds = Math.max(sleepingMaxSeconds, seconds);
                    }
                    String session = String.valueOf(sessionId);
                    sink.addObject("sqlserver.transaction.open_seconds", "session", session, seconds, ts);
                    sink.addObject("sqlserver.transaction.sleeping_open", "session", session, sleeping ? 1 : 0, ts);
                    snapshot.append("session=").append(sessionId)
                            .append("|database=").append(nullSafe(rs.getString("database_name")))
                            .append("|status=").append(nullSafe(rs.getString("status")))
                            .append("|seconds=").append(seconds)
                            .append("|login=").append(nullSafe(rs.getString("login_name")))
                            .append("|host=").append(nullSafe(rs.getString("host_name")))
                            .append("|program=").append(nullSafe(rs.getString("program_name")))
                            .append("|sql=").append(SqlServerSqlRedactor.redact(rs.getString("sql_text")))
                            .append('\n');
                }
            }
        }
        sink.addNumeric("sqlserver.transaction.open_count", count, ts);
        sink.addNumeric("sqlserver.transaction.max_seconds", maxSeconds, ts);
        sink.addNumeric("sqlserver.transaction.sleeping_open_count", sleepingCount, ts);
        sink.addNumeric("sqlserver.transaction.sleeping_open_max_seconds", sleepingMaxSeconds, ts);
        sink.addText("sqlserver.transaction.snapshot", snapshot.toString(), ts);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.replace('|', ' ');
    }
}