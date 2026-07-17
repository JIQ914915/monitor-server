package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

/** 低频能力探测：Query Store 当前状态和容量。 */
@Component
public class SqlServerCapabilityItem implements SqlServerMetricItem {
    public static final String CODE = "capability";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        if (!adapter.supportsQueryStore()) return;
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.queryStoreCapabilitySql())) {
                if (!rs.next()) return;
                sink.addNumeric("sqlserver.query_store.actual_state", rs.getDouble("actual_state"), ts);
                sink.addNumeric("sqlserver.query_store.desired_state", rs.getDouble("desired_state"), ts);
                sink.addNumeric("sqlserver.query_store.readonly_reason", rs.getDouble("readonly_reason"), ts);
                sink.addNumeric("sqlserver.query_store.current_size_mb", rs.getDouble("current_storage_size_mb"), ts);
                sink.addNumeric("sqlserver.query_store.max_size_mb", rs.getDouble("max_storage_size_mb"), ts);
                sink.addNumeric("sqlserver.query_store.capture_mode", rs.getDouble("query_capture_mode"), ts);
            }
        }
    }
}
