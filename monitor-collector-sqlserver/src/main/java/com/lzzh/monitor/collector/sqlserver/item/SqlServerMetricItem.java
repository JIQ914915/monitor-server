package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;

import java.sql.Connection;
import java.util.Set;

/** SQL Server 可插拔采集项。 */
public interface SqlServerMetricItem {
    int DEFAULT_QUERY_TIMEOUT_SECONDS = 5;

    String code();

    default Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.MINUTE);
    }

    void collect(Connection conn, CollectRequest request,
                 SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception;
}
