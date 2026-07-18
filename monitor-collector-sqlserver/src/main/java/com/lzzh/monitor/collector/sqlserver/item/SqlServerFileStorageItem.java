package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 数据库文件容量、自动增长配置及承载卷剩余空间。 */
@Component
public class SqlServerFileStorageItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "file_storage";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        SqlServerVersionAdapter adapter, SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        List<String> databases = accessibleUserDatabases(conn);
        if (databases.isEmpty()) return;
        String originalDatabase = currentDatabase(conn);
        Map<String, Volume> volumes = new LinkedHashMap<>();
        StringBuilder snapshot = new StringBuilder();
        int percentGrowthFiles = 0;
        int unlimitedFiles = 0;
        try {
            for (String database : databases) {
                try {
                    useDatabase(conn, database);
                    try (Statement st = conn.createStatement()) {
                        st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
                        try (ResultSet rs = st.executeQuery(adapter.fileCapacitySql())) {
                            while (rs.next()) {
                                String file = database + "/" + rs.getString("file_name");
                                double size = rs.getDouble("size_bytes");
                                sink.addObject("sqlserver.file.size_bytes", "database_file", file, size, ts);
                                double used = rs.getDouble("used_bytes");
                                if (!rs.wasNull()) sink.addObject("sqlserver.file.used_bytes", "database_file", file, used, ts);
                                double maxSize = rs.getDouble("max_size_bytes");
                                boolean unlimited = rs.wasNull();
                                if (unlimited) unlimitedFiles++;
                                else sink.addObject("sqlserver.file.max_size_bytes", "database_file", file, maxSize, ts);
                                boolean percent = rs.getBoolean("is_percent_growth");
                                double growth = rs.getDouble("growth_value");
                                if (percent) {
                                    percentGrowthFiles++;
                                    sink.addObject("sqlserver.file.growth_percent", "database_file", file, growth, ts);
                                } else {
                                    sink.addObject("sqlserver.file.growth_bytes", "database_file", file, growth, ts);
                                }
                                String mount = rs.getString("volume_mount_point");
                                long total = rs.getLong("volume_total_bytes");
                                boolean totalNull = rs.wasNull();
                                long available = rs.getLong("volume_available_bytes");
                                boolean availableNull = rs.wasNull();
                                if (mount != null && !totalNull && !availableNull) {
                                    volumes.put(mount, new Volume(total, available));
                                }
                                snapshot.append("file=").append(file)
                                        .append("|type=").append(rs.getString("type_desc"))
                                        .append("|size_bytes=").append(Math.round(size))
                                        .append("|growth=").append(Math.round(growth))
                                        .append(percent ? "%" : "B")
                                        .append("|volume=").append(mount == null ? "" : mount)
                                        .append('\n');
                            }
                        }
                    }
                } catch (SQLException e) {
                    sink.addItemError(code(), database + ": " + e.getMessage());
                }
            }
        } finally {
            useDatabase(conn, originalDatabase);
        }
        double minFreePercent = 100;
        for (Map.Entry<String, Volume> entry : volumes.entrySet()) {
            String mount = entry.getKey();
            Volume volume = entry.getValue();
            sink.addObject("sqlserver.volume.total_bytes", "volume", mount, volume.totalBytes(), ts);
            sink.addObject("sqlserver.volume.available_bytes", "volume", mount, volume.availableBytes(), ts);
            double freePercent = volume.totalBytes() <= 0 ? 0 : volume.availableBytes() * 100.0 / volume.totalBytes();
            sink.addObject("sqlserver.volume.free_percent", "volume", mount, freePercent, ts);
            minFreePercent = Math.min(minFreePercent, freePercent);
        }
        if (!volumes.isEmpty()) sink.addNumeric("sqlserver.volume.min_free_percent", minFreePercent, ts);
        sink.addNumeric("sqlserver.file.percent_growth_count", percentGrowthFiles, ts);
        sink.addNumeric("sqlserver.file.unlimited_growth_count", unlimitedFiles, ts);
        sink.addText("sqlserver.file.layout_snapshot", snapshot.toString(), ts);
    }

    private static List<String> accessibleUserDatabases(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT name FROM sys.databases
                 WHERE database_id>4 AND state_desc='ONLINE' AND source_database_id IS NULL
                   AND HAS_DBACCESS(name)=1 ORDER BY name
                """)) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    private static String currentDatabase(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT DB_NAME()")) {
            if (!rs.next()) throw new SQLException("无法读取当前数据库上下文");
            return rs.getString(1);
        }
    }

    private static void useDatabase(Connection conn, String database) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("USE [" + database.replace("]", "]]" ) + "]");
        }
    }

    private record Volume(long totalBytes, long availableBytes) {}
}