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

/** 各用户数据库 VLF 数量和活动 VLF 数量。 */
@Component
public class SqlServerLogHealthItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "log_health";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        List<String> databases = accessibleUserDatabases(conn);
        String originalDatabase = currentDatabase(conn);
        int totalVlf = 0;
        int maxVlf = 0;
        int totalActive = 0;
        try {
            for (String database : databases) {
                try {
                    useDatabase(conn, database);
                    Counts counts = collectVlf(conn, adapter);
                    totalVlf += counts.total();
                    totalActive += counts.active();
                    maxVlf = Math.max(maxVlf, counts.total());
                    sink.addObject("sqlserver.log.vlf_count", "database", database, counts.total(), ts);
                    sink.addObject("sqlserver.log.active_vlf_count", "database", database, counts.active(), ts);
                } catch (SQLException e) {
                    sink.addItemError(code(), database + ": " + e.getMessage());
                }
            }
        } finally {
            useDatabase(conn, originalDatabase);
        }
        sink.addNumeric("sqlserver.log.vlf_total_count", totalVlf, ts);
        sink.addNumeric("sqlserver.log.vlf_max_count", maxVlf, ts);
        sink.addNumeric("sqlserver.log.active_vlf_total_count", totalActive, ts);
    }

    private static Counts collectVlf(Connection conn, SqlServerVersionAdapter adapter) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            if (adapter.supportsVlfDmv()) {
                try (ResultSet rs = st.executeQuery(adapter.vlfSql())) {
                    if (!rs.next()) return new Counts(0, 0);
                    return new Counts(rs.getInt("vlf_count"), rs.getInt("active_vlf_count"));
                }
            }
            int total = 0;
            int active = 0;
            boolean hasResult = st.execute(adapter.vlfSql());
            while (hasResult) {
                try (ResultSet rs = st.getResultSet()) {
                    while (rs != null && rs.next()) {
                        total++;
                        if (rs.getInt("Status") == 2) active++;
                    }
                }
                hasResult = st.getMoreResults();
            }
            return new Counts(total, active);
        }
    }

    private static List<String> accessibleUserDatabases(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT name FROM sys.databases
                 WHERE database_id>4 AND state_desc='ONLINE' AND source_database_id IS NULL
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

    private record Counts(int total, int active) {}
}