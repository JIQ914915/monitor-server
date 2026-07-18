package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/** tempdb 空间、文件布局和当前 PAGELATCH 分配争用。 */
@Component
public class SqlServerTempdbItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "tempdb";
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        collectSpace(conn, sink, ts);
        collectLayout(conn, sink, ts);
        collectPageLatch(conn, sink, ts);
    }

    private static void collectSpace(Connection conn, SqlServerMetricSink sink, long ts) throws Exception {
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

    private static void collectLayout(Connection conn, SqlServerMetricSink sink, long ts) throws Exception {
        int dataFileCount = 0;
        int percentGrowthCount = 0;
        double minSize = Double.MAX_VALUE;
        double maxSize = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT f.file_id,f.name,f.type,f.size*8192.0 AS size_bytes,f.is_percent_growth,
                           CASE WHEN f.is_percent_growth=1 THEN f.growth ELSE f.growth*8192.0 END AS growth_value,
                           vs.volume_mount_point,vs.total_bytes,vs.available_bytes
                      FROM tempdb.sys.database_files f
                      OUTER APPLY sys.dm_os_volume_stats(2,f.file_id) vs
                     ORDER BY f.file_id
                    """)) {
                while (rs.next()) {
                    String file = rs.getString("name");
                    double size = rs.getDouble("size_bytes");
                    boolean dataFile = rs.getInt("type") == 0;
                    if (dataFile) {
                        dataFileCount++;
                        minSize = Math.min(minSize, size);
                        maxSize = Math.max(maxSize, size);
                    }
                    boolean percent = rs.getBoolean("is_percent_growth");
                    if (percent) percentGrowthCount++;
                    sink.addObject("sqlserver.tempdb.file_size_bytes", "tempdb_file", file, size, ts);
                    sink.addObject(percent ? "sqlserver.tempdb.file_growth_percent" : "sqlserver.tempdb.file_growth_bytes",
                            "tempdb_file", file, rs.getDouble("growth_value"), ts);
                    String mount = rs.getString("volume_mount_point");
                    long available = rs.getLong("available_bytes");
                    if (mount != null && !rs.wasNull()) {
                        sink.addObject("sqlserver.tempdb.volume_available_bytes", "volume", mount, available, ts);
                    }
                }
            }
        }
        sink.addNumeric("sqlserver.tempdb.data_file_count", dataFileCount, ts);
        sink.addNumeric("sqlserver.tempdb.percent_growth_file_count", percentGrowthCount, ts);
        double sizeSkew = dataFileCount <= 1 || maxSize <= 0 ? 0 : (maxSize - minSize) * 100.0 / maxSize;
        sink.addNumeric("sqlserver.tempdb.data_file_size_skew_percent", sizeSkew, ts);
    }

    private static void collectPageLatch(Connection conn, SqlServerMetricSink sink, long ts) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT COUNT(*) AS waiting_tasks,
                           COALESCE(MAX(wait_duration_ms),0) AS max_wait_ms
                      FROM sys.dm_os_waiting_tasks
                     WHERE wait_type LIKE 'PAGELATCH[_]%'
                       AND resource_description LIKE '2:%'
                    """)) {
                if (!rs.next()) return;
                sink.addNumeric("sqlserver.tempdb.pagelatch_waiting_tasks", rs.getDouble("waiting_tasks"), ts);
                sink.addNumeric("sqlserver.tempdb.pagelatch_max_wait_ms", rs.getDouble("max_wait_ms"), ts);
            }
        }
    }
}