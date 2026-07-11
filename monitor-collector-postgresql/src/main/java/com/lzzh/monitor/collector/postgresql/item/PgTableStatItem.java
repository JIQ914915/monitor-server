package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：表热点与索引使用对象级分析（二期 D2，对标 MySQL TableIoStatItem）。
 *
 * <p><b>小时级</b>：{@code pg_stat_user_tables} 节点内差值，得到最近一小时各表的
 * 顺扫/索引扫描次数与行读写量，按总行访问量取 Top N 写对象指标
 * （object_type=table，object_name=schema.table）：
 * <ul>
 *   <li>{@code pgtable.seq_scan} —— 本周期顺序扫描次数（全表扫描热点信号）；</li>
 *   <li>{@code pgtable.idx_scan} —— 本周期索引扫描次数；</li>
 *   <li>{@code pgtable.read_rows} —— 本周期读取行数（seq_tup_read + idx_tup_fetch）；</li>
 *   <li>{@code pgtable.write_rows} —— 本周期写入行数（insert+update+delete）。</li>
 * </ul>
 *
 * <p><b>天级</b>：
 * <ul>
 *   <li>疑似未使用索引（{@code pg_stat_user_indexes.idx_scan=0}，排除主键/唯一约束索引），
 *       产出文本指标 {@code pg.index.unused_list}（JSON 结构与 MySQL 版一致，服务端复用同一解析）；
 *       统计自 stats reset 累计，实例运行时间短时结论不可靠，一并输出 uptimeDays；</li>
 *   <li>失效索引（{@code pg_index.indisvalid=false}，CREATE INDEX CONCURRENTLY 失败残留），
 *       产出文本指标 {@code pg.index.invalid_list}。</li>
 * </ul>
 * 首轮建基线不产出（小时级）。
 */
@Component
public class PgTableStatItem implements PgMetricItem {

    public static final String CODE = "pg_table_stat";

    /** 小时级热点表保留上限（按行访问量降序），支撑管理端真实分页。 */
    private static final int TOP_N = 200;
    /** 天级疑似未使用索引保留上限。 */
    private static final int UNUSED_INDEX_LIMIT = 500;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    /** instanceId → (schema.table → [seqScan, idxScan, readRows, writeRows])。 */
    private final Map<Long, Map<String, long[]>> baselines = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY, CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        if (request.getFrequency() == CollectFrequency.HOURLY) {
            collectTableHotspots(conn, request.getInstanceId(), sink);
        } else if (request.getFrequency() == CollectFrequency.DAILY) {
            collectUnusedIndexes(conn, sink);
            collectInvalidIndexes(conn, sink);
        }
    }

    // ---- 小时级：表热点（差值 Top N）----

    private void collectTableHotspots(Connection conn, long instanceId, PgMetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();

        Map<String, long[]> current = new HashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT schemaname, relname,
                           COALESCE(seq_scan, 0)                                 AS seq_scan,
                           COALESCE(idx_scan, 0)                                 AS idx_scan,
                           COALESCE(seq_tup_read, 0) + COALESCE(idx_tup_fetch, 0) AS read_rows,
                           COALESCE(n_tup_ins, 0) + COALESCE(n_tup_upd, 0) + COALESCE(n_tup_del, 0) AS write_rows
                      FROM pg_stat_user_tables
                    """)) {
                while (rs.next()) {
                    current.put(rs.getString("schemaname") + "." + rs.getString("relname"),
                            new long[]{rs.getLong("seq_scan"), rs.getLong("idx_scan"),
                                    rs.getLong("read_rows"), rs.getLong("write_rows")});
                }
            }
        }

        Map<String, long[]> prev = baselines.put(instanceId, current);
        if (prev == null) {
            return;
        }

        record Delta(String name, long seqScan, long idxScan, long readRows, long writeRows) {
        }
        List<Delta> deltas = new ArrayList<>();
        for (Map.Entry<String, long[]> e : current.entrySet()) {
            long[] cur = e.getValue();
            long[] base = prev.get(e.getKey());
            long seq = base == null || cur[0] < base[0] ? cur[0] : cur[0] - base[0];
            long idx = base == null || cur[1] < base[1] ? cur[1] : cur[1] - base[1];
            long read = base == null || cur[2] < base[2] ? cur[2] : cur[2] - base[2];
            long write = base == null || cur[3] < base[3] ? cur[3] : cur[3] - base[3];
            if (seq == 0 && idx == 0 && read == 0 && write == 0) {
                continue;
            }
            deltas.add(new Delta(e.getKey(), seq, idx, read, write));
        }
        deltas.sort(Comparator.comparingLong(d -> -(d.readRows() + d.writeRows())));

        int limit = Math.min(deltas.size(), TOP_N);
        for (int i = 0; i < limit; i++) {
            Delta d = deltas.get(i);
            sink.addObject("pgtable.seq_scan", "table", d.name(), d.seqScan(), ts);
            sink.addObject("pgtable.idx_scan", "table", d.name(), d.idxScan(), ts);
            sink.addObject("pgtable.read_rows", "table", d.name(), d.readRows(), ts);
            sink.addObject("pgtable.write_rows", "table", d.name(), d.writeRows(), ts);
        }
    }

    // ---- 天级：疑似未使用索引 ----

    private void collectUnusedIndexes(Connection conn, PgMetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();

        long uptimeDays = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(
                    "SELECT EXTRACT(EPOCH FROM (now() - pg_postmaster_start_time()))::bigint")) {
                if (rs.next()) {
                    uptimeDays = rs.getLong(1) / 86400;
                }
            }
        }

        List<String> rows = new ArrayList<>();
        // 排除主键与唯一约束背后的索引（虽无扫描但承担约束职责，不能删）
        String sql = """
                SELECT s.schemaname, s.relname, s.indexrelname
                  FROM pg_stat_user_indexes s
                  JOIN pg_index i ON i.indexrelid = s.indexrelid
                 WHERE s.idx_scan = 0
                   AND NOT i.indisprimary
                   AND NOT i.indisunique
                 ORDER BY s.schemaname, s.relname, s.indexrelname
                 LIMIT """ + " " + UNUSED_INDEX_LIMIT;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    rows.add("{\"schema\":\"" + escape(rs.getString(1))
                            + "\",\"table\":\"" + escape(rs.getString(2))
                            + "\",\"index\":\"" + escape(rs.getString(3)) + "\"}");
                }
            }
        }

        String json = "{\"uptimeDays\":" + uptimeDays
                + ",\"indexes\":[" + String.join(",", rows) + "]}";
        sink.addText("pg.index.unused_list", json, ts);
    }

    // ---- 天级：失效索引（CREATE INDEX CONCURRENTLY 失败残留）----

    private void collectInvalidIndexes(Connection conn, PgMetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();
        List<String> rows = new ArrayList<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT n.nspname, t.relname AS table_name, c.relname AS index_name
                      FROM pg_index i
                      JOIN pg_class c ON c.oid = i.indexrelid
                      JOIN pg_class t ON t.oid = i.indrelid
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE NOT i.indisvalid
                       AND n.nspname NOT IN ('pg_catalog', 'pg_toast')
                     LIMIT 100
                    """)) {
                while (rs.next()) {
                    rows.add("{\"schema\":\"" + escape(rs.getString(1))
                            + "\",\"table\":\"" + escape(rs.getString(2))
                            + "\",\"index\":\"" + escape(rs.getString(3)) + "\"}");
                }
            }
        }
        sink.addText("pg.index.invalid_list", "{\"indexes\":[" + String.join(",", rows) + "]}", ts);
    }

    /** 实例删除/暂停时清理基线。 */
    public void evict(long instanceId) {
        baselines.remove(instanceId);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
