package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** 吞吐、连接、内存与 Buffer Pool 核心指标。 */
@Component
public class SqlServerPerformanceItem implements SqlServerMetricItem {
    private final SqlServerCounterDeltaStore deltaStore;

    public SqlServerPerformanceItem(SqlServerCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override public String code() { return "performance"; }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.performanceCountersSql())) {
                if (!rs.next()) return;
                gauge(rs, sink, "sqlserver.conn.user", "user_connections", ts);
                gauge(rs, sink, "sqlserver.memory.grants_pending", "memory_grants_pending", ts);
                gauge(rs, sink, "sqlserver.memory.grants_outstanding", "memory_grants_outstanding", ts);
                gaugeBytes(rs, sink, "sqlserver.memory.total_bytes", "total_server_memory_kb", ts);
                gaugeBytes(rs, sink, "sqlserver.memory.target_bytes", "target_server_memory_kb", ts);
                gauge(rs, sink, "sqlserver.buffer.ple_seconds", "page_life_expectancy", ts);
                rate(rs, request, sink, "sqlserver.batch_requests_per_sec", "batch_requests_total", ts);
                rate(rs, request, sink, "sqlserver.compilations_per_sec", "compilations_total", ts);
                rate(rs, request, sink, "sqlserver.recompilations_per_sec", "recompilations_total", ts);
                rate(rs, request, sink, "sqlserver.lazy_writes_per_sec", "lazy_writes_total", ts);
                rate(rs, request, sink, "sqlserver.page_reads_per_sec", "page_reads_total", ts);
                rate(rs, request, sink, "sqlserver.page_writes_per_sec", "page_writes_total", ts);
                rate(rs, request, sink, "sqlserver.deadlocks_per_sec", "deadlocks_total", ts);
            }
        }
    }

    private void rate(ResultSet rs, CollectRequest request, SqlServerMetricSink sink,
                      String metric, String column, long ts) throws Exception {
        double value = rs.getDouble(column);
        if (rs.wasNull()) return;
        deltaStore.rate(request.getInstanceId(), metric, value, ts)
                .ifPresent(rate -> sink.addNumeric(metric, rate, ts));
    }

    private static void gauge(ResultSet rs, SqlServerMetricSink sink,
                              String metric, String column, long ts) throws Exception {
        double value = rs.getDouble(column);
        if (!rs.wasNull()) sink.addNumeric(metric, value, ts);
    }

    private static void gaugeBytes(ResultSet rs, SqlServerMetricSink sink,
                                   String metric, String column, long ts) throws Exception {
        double value = rs.getDouble(column);
        if (!rs.wasNull()) sink.addNumeric(metric, value * 1024.0, ts);
    }
}
