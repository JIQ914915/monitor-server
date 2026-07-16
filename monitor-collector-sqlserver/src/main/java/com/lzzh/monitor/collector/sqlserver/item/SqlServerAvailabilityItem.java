package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** 实例连接可用性、运行时长和身份元数据。 */
@Component
public class SqlServerAvailabilityItem implements SqlServerMetricItem {
    public static final String CODE = "availability";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        sink.addNumeric("sqlserver.availability", 1, ts);
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.identitySql())) {
                if (!rs.next()) return;
                sink.addNumeric("sqlserver.uptime", rs.getDouble("uptime_seconds"), ts);
                sink.addNumeric("sqlserver.capability.clustered", rs.getDouble("is_clustered"), ts);
                sink.addNumeric("sqlserver.capability.always_on", rs.getDouble("is_hadr_enabled"), ts);
                sink.addNumeric("sqlserver.identity.engine_edition", rs.getDouble("engine_edition"), ts);
                sink.addText("sqlserver.identity.product_version", rs.getString("product_version"), ts);
                sink.addText("sqlserver.identity.edition", rs.getString("edition"), ts);
            }
        }
    }
}
