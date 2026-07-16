package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

/** 基于采样差值的等待分类指标，避免使用自启动以来累计值直接告警。 */
@Component
public class SqlServerWaitStatsItem implements SqlServerMetricItem {
    private static final Set<String> CATEGORIES =
            Set.of("cpu", "io", "lock", "log", "memory", "network", "parallel", "ha", "other");
    private final SqlServerCounterDeltaStore deltaStore;

    public SqlServerWaitStatsItem(SqlServerCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override public String code() { return "wait_stats"; }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.waitStatsSql())) {
                while (rs.next()) {
                    String category = rs.getString("wait_category");
                    if (!CATEGORIES.contains(category)) category = "other";
                    emitRate(request, sink, "sqlserver.wait." + category + ".ms_per_sec",
                            rs.getDouble("wait_time_ms"), ts);
                    emitRate(request, sink, "sqlserver.wait." + category + ".tasks_per_sec",
                            rs.getDouble("waiting_tasks_count"), ts);
                    emitRate(request, sink, "sqlserver.wait." + category + ".signal_ms_per_sec",
                            rs.getDouble("signal_wait_time_ms"), ts);
                }
            }
        }
    }

    private void emitRate(CollectRequest request, SqlServerMetricSink sink,
                          String metric, double value, long ts) {
        deltaStore.rate(request.getInstanceId(), metric, value, ts)
                .ifPresent(rate -> sink.addNumeric(metric, rate, ts));
    }
}
