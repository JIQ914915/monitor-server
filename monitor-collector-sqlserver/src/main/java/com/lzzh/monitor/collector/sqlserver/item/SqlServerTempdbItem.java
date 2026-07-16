package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** tempdb 空间构成，区分用户对象、内部对象、版本存储与可用空间。 */
@Component
public class SqlServerTempdbItem implements SqlServerMetricItem {
    @Override public String code() { return "tempdb"; }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT SUM(user_object_reserved_page_count)*8192.0 AS user_bytes,
                           SUM(internal_object_reserved_page_count)*8192.0 AS internal_bytes,
                           SUM(version_store_reserved_page_count)*8192.0 AS version_store_bytes,
                           SUM(unallocated_extent_page_count)*8192.0 AS free_bytes
                    FROM tempdb.sys.dm_db_file_space_usage
                    """)) {
                if (!rs.next()) return;
                sink.addNumeric("sqlserver.tempdb.user_bytes", rs.getDouble("user_bytes"), ts);
                sink.addNumeric("sqlserver.tempdb.internal_bytes", rs.getDouble("internal_bytes"), ts);
                sink.addNumeric("sqlserver.tempdb.version_store_bytes", rs.getDouble("version_store_bytes"), ts);
                sink.addNumeric("sqlserver.tempdb.free_bytes", rs.getDouble("free_bytes"), ts);
            }
        }
    }
}
