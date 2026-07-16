package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.OptionalDouble;

/** 当前连接库的数据/日志容量、日志复用阻塞与文件 I/O 延迟。 */
@Component
public class SqlServerStorageItem implements SqlServerMetricItem {
    private final SqlServerCounterDeltaStore deltaStore;

    public SqlServerStorageItem(SqlServerCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override public String code() { return "storage"; }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.storageSql())) {
                if (!rs.next()) return;
                emit(rs, sink, "sqlserver.storage.data_size_bytes", "data_size_bytes", ts);
                emit(rs, sink, "sqlserver.storage.data_used_bytes", "data_used_bytes", ts);
                emit(rs, sink, "sqlserver.storage.log_size_bytes", "log_size_bytes", ts);
                emit(rs, sink, "sqlserver.storage.log_used_percent", "log_used_percent", ts);
                emit(rs, sink, "sqlserver.storage.log_reuse_blocked", "log_reuse_blocked", ts);
                OptionalDouble reads = deltaStore.rate(request.getInstanceId(), "io.reads",
                        rs.getDouble("io_reads"), ts);
                OptionalDouble writes = deltaStore.rate(request.getInstanceId(), "io.writes",
                        rs.getDouble("io_writes"), ts);
                OptionalDouble readStall = deltaStore.rate(request.getInstanceId(), "io.read_stall",
                        rs.getDouble("io_stall_read_ms"), ts);
                OptionalDouble writeStall = deltaStore.rate(request.getInstanceId(), "io.write_stall",
                        rs.getDouble("io_stall_write_ms"), ts);
                if (reads.isPresent()) sink.addNumeric("sqlserver.io.reads_per_sec", reads.getAsDouble(), ts);
                if (writes.isPresent()) sink.addNumeric("sqlserver.io.writes_per_sec", writes.getAsDouble(), ts);
                if (reads.isPresent() && reads.getAsDouble() > 0 && readStall.isPresent()) {
                    sink.addNumeric("sqlserver.io.read_latency_ms",
                            readStall.getAsDouble() / reads.getAsDouble(), ts);
                }
                if (writes.isPresent() && writes.getAsDouble() > 0 && writeStall.isPresent()) {
                    sink.addNumeric("sqlserver.io.write_latency_ms",
                            writeStall.getAsDouble() / writes.getAsDouble(), ts);
                }
            }
        }
    }

    private static void emit(ResultSet rs, SqlServerMetricSink sink,
                             String metric, String column, long ts) throws Exception {
        double value = rs.getDouble(column);
        if (!rs.wasNull()) sink.addNumeric(metric, value, ts);
    }
}
