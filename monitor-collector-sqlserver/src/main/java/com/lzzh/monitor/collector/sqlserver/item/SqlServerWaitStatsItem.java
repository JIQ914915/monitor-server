package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** 等待类型原始证据与窗口差值分类指标，避免用实例启动以来累计值直接告警。 */
@Component
public class SqlServerWaitStatsItem implements SqlServerMetricItem {
    private static final int RAW_TOP_N = 20;
    private final SqlServerCounterDeltaStore deltaStore;

    public SqlServerWaitStatsItem(SqlServerCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return "wait_stats";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        Map<String, double[]> categoryTotals = new LinkedHashMap<>();
        List<WaitRate> rawRates = new ArrayList<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.waitStatsSql())) {
                while (rs.next()) {
                    String waitType = rs.getString("wait_type");
                    if (waitType == null || waitType.isBlank()) continue;
                    double waitTime = rs.getDouble("wait_time_ms");
                    double signalTime = rs.getDouble("signal_wait_time_ms");
                    double tasks = rs.getDouble("waiting_tasks_count");
                    double[] totals = categoryTotals.computeIfAbsent(categoryOf(waitType), ignored -> new double[3]);
                    totals[0] += waitTime;
                    totals[1] += signalTime;
                    totals[2] += tasks;
                    OptionalDouble rate = deltaStore.rate(request.getInstanceId(),
                            "wait.type." + waitType + ".ms", waitTime, ts);
                    rate.ifPresent(value -> rawRates.add(new WaitRate(waitType, value)));
                }
            }
        }
        categoryTotals.forEach((category, values) -> {
            emitRate(request, sink, "sqlserver.wait." + category + ".ms_per_sec", values[0], ts);
            emitRate(request, sink, "sqlserver.wait." + category + ".signal_ms_per_sec", values[1], ts);
            emitRate(request, sink, "sqlserver.wait." + category + ".tasks_per_sec", values[2], ts);
        });
        rawRates.stream().filter(value -> value.rate() > 0)
                .sorted(Comparator.comparingDouble(WaitRate::rate).reversed())
                .limit(RAW_TOP_N)
                .forEach(value -> sink.addObject("sqlserver.wait.type.ms_per_sec",
                        "wait_type", value.waitType(), value.rate(), ts));
    }

    static String categoryOf(String waitType) {
        if (waitType.startsWith("LCK_")) return "lock";
        if (waitType.startsWith("PAGEIOLATCH_") || waitType.startsWith("IO_COMPLETION")) return "io";
        if (waitType.startsWith("WRITELOG")) return "log";
        if (waitType.startsWith("RESOURCE_SEMAPHORE")) return "memory";
        if (waitType.startsWith("CXPACKET") || waitType.startsWith("CXCONSUMER")) return "parallel";
        if (waitType.startsWith("HADR_") || waitType.startsWith("REDO_")) return "ha";
        if (waitType.startsWith("ASYNC_NETWORK_IO")) return "network";
        if (waitType.startsWith("SOS_SCHEDULER_YIELD")) return "cpu";
        return "other";
    }

    private void emitRate(CollectRequest request, SqlServerMetricSink sink,
                          String metric, double value, long ts) {
        deltaStore.rate(request.getInstanceId(), metric, value, ts)
                .ifPresent(rate -> sink.addNumeric(metric, rate, ts));
    }

    private record WaitRate(String waitType, double rate) {}
}