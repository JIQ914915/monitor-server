package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** 所有可访问用户数据库的数据/日志容量、日志复用状态与文件 I/O。 */
@Component
public class SqlServerStorageItem implements SqlServerMetricItem {
    private final SqlServerCounterDeltaStore deltaStore;

    public SqlServerStorageItem(SqlServerCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return "storage";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        List<String> databases = accessibleUserDatabases(conn);
        if (databases.isEmpty()) return;

        String originalDatabase = currentDatabase(conn);
        double dataSize = 0;
        double dataUsed = 0;
        double logSize = 0;
        double ioReads = 0;
        double ioWrites = 0;
        double readStall = 0;
        double writeStall = 0;
        Double maxLogUsed = null;
        int reuseBlocked = 0;
        int collected = 0;
        StringBuilder reuseSnapshot = new StringBuilder();
        try {
            for (String database : databases) {
                try (Statement st = conn.createStatement()) {
                    st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
                    st.execute("USE " + quoteIdentifier(database));
                    try (ResultSet rs = st.executeQuery(adapter.storageSql())) {
                        if (!rs.next()) continue;
                        Double databaseDataSize = nullable(rs, "data_size_bytes");
                        Double databaseDataUsed = nullable(rs, "data_used_bytes");
                        Double databaseLogSize = nullable(rs, "log_size_bytes");
                        Double databaseLogUsed = nullable(rs, "log_used_percent");
                        Double databaseReads = nullable(rs, "io_reads");
                        Double databaseWrites = nullable(rs, "io_writes");
                        Double databaseReadStall = nullable(rs, "io_stall_read_ms");
                        Double databaseWriteStall = nullable(rs, "io_stall_write_ms");
                        String reuseWait = rs.getString("log_reuse_wait_desc");
                        collected++;
                        dataSize += zero(databaseDataSize);
                        dataUsed += zero(databaseDataUsed);
                        logSize += zero(databaseLogSize);
                        ioReads += zero(databaseReads);
                        ioWrites += zero(databaseWrites);
                        readStall += zero(databaseReadStall);
                        writeStall += zero(databaseWriteStall);
                        if (databaseLogUsed != null) {
                            maxLogUsed = maxLogUsed == null ? databaseLogUsed : Math.max(maxLogUsed, databaseLogUsed);
                        }
                        boolean blocked = reuseWait != null && !"NOTHING".equals(reuseWait);
                        if (blocked) reuseBlocked++;
                        reuseSnapshot.append(safeName(database)).append('=').append(reuseWait == null ? "UNKNOWN" : reuseWait).append('\n');
                        object(sink, "sqlserver.storage.data_size_bytes", database, databaseDataSize, ts);
                        object(sink, "sqlserver.storage.data_used_bytes", database, databaseDataUsed, ts);
                        object(sink, "sqlserver.storage.log_size_bytes", database, databaseLogSize, ts);
                        object(sink, "sqlserver.storage.log_used_percent", database, databaseLogUsed, ts);
                        sink.addObject("sqlserver.storage.log_reuse_blocked", "database", database, blocked ? 1 : 0, ts);
                    }
                } catch (SQLException e) {
                    sink.addItemError("storage[" + safeName(database) + "]", safeMessage(e));
                }
            }
        } finally {
            if (originalDatabase != null && !originalDatabase.isBlank()) {
                try (Statement st = conn.createStatement()) {
                    st.execute("USE " + quoteIdentifier(originalDatabase));
                }
            }
        }
        if (collected == 0) return;
        sink.addNumeric("sqlserver.storage.data_size_bytes", dataSize, ts);
        sink.addNumeric("sqlserver.storage.data_used_bytes", dataUsed, ts);
        sink.addNumeric("sqlserver.storage.log_size_bytes", logSize, ts);
        if (maxLogUsed != null) sink.addNumeric("sqlserver.storage.log_used_percent", maxLogUsed, ts);
        sink.addNumeric("sqlserver.storage.log_reuse_blocked", reuseBlocked, ts);
        sink.addText("sqlserver.storage.log_reuse_wait_snapshot", reuseSnapshot.toString(), ts);

        OptionalDouble reads = deltaStore.rate(request.getInstanceId(), "io.reads", ioReads, ts);
        OptionalDouble writes = deltaStore.rate(request.getInstanceId(), "io.writes", ioWrites, ts);
        OptionalDouble readStallRate = deltaStore.rate(request.getInstanceId(), "io.read_stall", readStall, ts);
        OptionalDouble writeStallRate = deltaStore.rate(request.getInstanceId(), "io.write_stall", writeStall, ts);
        reads.ifPresent(value -> sink.addNumeric("sqlserver.io.reads_per_sec", value, ts));
        writes.ifPresent(value -> sink.addNumeric("sqlserver.io.writes_per_sec", value, ts));
        if (reads.isPresent() && reads.getAsDouble() > 0 && readStallRate.isPresent()) {
            sink.addNumeric("sqlserver.io.read_latency_ms", readStallRate.getAsDouble() / reads.getAsDouble(), ts);
        }
        if (writes.isPresent() && writes.getAsDouble() > 0 && writeStallRate.isPresent()) {
            sink.addNumeric("sqlserver.io.write_latency_ms", writeStallRate.getAsDouble() / writes.getAsDouble(), ts);
        }
    }

    private static List<String> accessibleUserDatabases(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT name FROM sys.databases
                     WHERE database_id > 4 AND source_database_id IS NULL
                       AND state_desc='ONLINE' AND HAS_DBACCESS(name)=1
                     ORDER BY name
                    """)) {
                while (rs.next()) result.add(rs.getString(1));
            }
        }
        return result;
    }

    private static String currentDatabase(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT DB_NAME()")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static String quoteIdentifier(String value) {
        return "[" + value.replace("]", "]]" ) + "]";
    }

    private static Double nullable(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static double zero(Double value) {
        return value == null ? 0 : value;
    }

    private static void object(SqlServerMetricSink sink, String metric, String database, Double value, long ts) {
        if (value != null) sink.addObject(metric, "database", database, value, ts);
    }

    private static String safeName(String value) {
        return value == null ? "unknown" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String safeMessage(SQLException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}