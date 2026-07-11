package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * 采集项：复制状态（§7.3 复制域，分钟级）。
 * <p>版本感知：5.6/5.7 用 {@code SHOW SLAVE STATUS}、8.0.22+ 用 {@code SHOW REPLICA STATUS}
 * （由版本适配器 {@link MySqlVersionAdapter#replicaStatusSql()} 提供）。两套结果集列名不同
 * （Slave_* / Seconds_Behind_Master ↔ Replica_* / Seconds_Behind_Source），此处按大小写不敏感
 * 的列名映射统一取值，兼容两种术语。
 * <p>产出指标（数值化便于告警与健康评分）：
 * <ul>
 *   <li>mysql.replication.is_replica：是否为从库（有结果行=1，否则=0）；</li>
 *   <li>mysql.replication.seconds_behind：复制延迟秒数（NULL 记为 -1，表示线程未运行/未知）；</li>
 *   <li>mysql.replication.io_running / sql_running：IO / SQL 线程是否 Yes（1/0）。</li>
 * </ul>
 * 另将复制错误信息（Last_Error / Last_IO_Error / Last_SQL_Error）作为文本指标
 * mysql.replication.last_error 走覆盖变更存储（§9.1，仅在错误信息变化时落库）。
 */
@Component
public class ReplicationItem implements MySqlMetricItem {

    public static final String CODE = "replication";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.replicaStatusSql())) {
            if (!rs.next()) {
                // 无结果 = 非从库实例，明确标记，避免"没数据"歧义
                sink.addNumeric("mysql.replication.is_replica", 0, ts);
                return;
            }
            Map<String, String> row = readRowCaseInsensitive(rs);
            sink.addNumeric("mysql.replication.is_replica", 1, ts);

            String secondsBehind = firstNonNull(row, "seconds_behind_master", "seconds_behind_source");
            sink.addNumeric("mysql.replication.seconds_behind", parseSeconds(secondsBehind), ts);

            String ioRunning = firstNonNull(row, "slave_io_running", "replica_io_running");
            String sqlRunning = firstNonNull(row, "slave_sql_running", "replica_sql_running");
            sink.addNumeric("mysql.replication.io_running", yesToOne(ioRunning), ts);
            sink.addNumeric("mysql.replication.sql_running", yesToOne(sqlRunning), ts);

            // 错误信息作为文本指标，覆盖变更存储（仅变化时落库）
            String lastError = firstNonBlank(
                    firstNonNull(row, "last_error"),
                    firstNonNull(row, "last_sql_error"),
                    firstNonNull(row, "last_io_error"));
            sink.addText("mysql.replication.last_error", lastError == null ? "" : lastError, ts);
            }
        }
    }

    private static Map<String, String> readRowCaseInsensitive(ResultSet rs) throws SQLException {
        Map<String, String> map = new HashMap<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            map.put(md.getColumnLabel(i).toLowerCase(), rs.getString(i));
        }
        return map;
    }

    private static String firstNonNull(Map<String, String> row, String... keys) {
        for (String k : keys) {
            if (row.containsKey(k)) {
                return row.get(k);
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static double parseSeconds(String raw) {
        if (raw == null || raw.isBlank() || "NULL".equalsIgnoreCase(raw.trim())) {
            return -1;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double yesToOne(String raw) {
        return "Yes".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? 1 : 0;
    }
}
