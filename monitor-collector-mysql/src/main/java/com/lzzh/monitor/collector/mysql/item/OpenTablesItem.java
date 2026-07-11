package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集项：表级锁监控（§15.4.1 二期 5.6 专项增强，分钟级，全版本通用）。
 *
 * <p>行锁监控（InnoDB）覆盖不到 MyISAM 表锁、LOCK TABLES、DDL/RENAME 造成的表级锁定，
 * 这在混用 MyISAM 的 5.6 老环境里是排障盲区。本项通过 {@code SHOW OPEN TABLES} 兜底：
 * <ul>
 *   <li>{@code mysql.lock.table_in_use_count} —— 当前被会话占用（In_use > 0）的表数量；</li>
 *   <li>{@code mysql.lock.table_name_locked_count} —— 当前被名字锁定（RENAME/DROP 进行中）的表数量；</li>
 *   <li>{@code mysql.lock.open_tables_detail}（文本）—— 占用表明细 Top 20 JSON
 *       {@code [{"db","table","inUse","nameLocked"}]}，按占用会话数降序。</li>
 * </ul>
 * 配合 GlobalStatusItem 新增的 {@code mysql.rate.Table_locks_waited}（表锁等待速率）
 * 可判断表级锁竞争趋势。无表被占用时产出 0 值（保持趋势图连续）。
 */
@Component
public class OpenTablesItem implements MySqlMetricItem {

    public static final String CODE = "open_tables";

    private static final int TOP_N = 20;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();

        long inUseCount = 0;
        long nameLockedCount = 0;
        List<String[]> rows = new ArrayList<>();   // [db, table, inUse, nameLocked]

        String sql = "SHOW OPEN TABLES WHERE In_use > 0 OR Name_locked > 0";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String db = rs.getString(1);
                    String table = rs.getString(2);
                    long inUse = rs.getLong(3);
                    long nameLocked = rs.getLong(4);
                    if (inUse > 0) {
                        inUseCount++;
                    }
                    if (nameLocked > 0) {
                        nameLockedCount++;
                    }
                    rows.add(new String[]{nvl(db), nvl(table),
                            String.valueOf(inUse), String.valueOf(nameLocked)});
                }
            }
        }

        sink.addNumeric("mysql.lock.table_in_use_count", inUseCount, ts);
        sink.addNumeric("mysql.lock.table_name_locked_count", nameLockedCount, ts);

        if (!rows.isEmpty()) {
            rows.sort((a, b) -> Long.compare(Long.parseLong(b[2]), Long.parseLong(a[2])));
            StringBuilder sb = new StringBuilder("[");
            int limit = Math.min(rows.size(), TOP_N);
            for (int i = 0; i < limit; i++) {
                String[] r = rows.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"db\":\"").append(escape(r[0]))
                        .append("\",\"table\":\"").append(escape(r[1]))
                        .append("\",\"inUse\":").append(r[2])
                        .append(",\"nameLocked\":").append(r[3]).append('}');
            }
            sink.addText("mysql.lock.open_tables_detail", sb.append(']').toString(), ts);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
