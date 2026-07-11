package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
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
 * 采集项：表 I/O / 索引使用对象级深度分析（§8.1 / 15.3 二期，5.7/8.0）。
 *
 * <p><b>小时级</b>：{@code performance_schema.table_io_waits_summary_by_table} 节点内差值，
 * 得到最近一小时各表的读/写操作次数与 I/O 等待耗时，按等待耗时取 Top N 写对象指标
 * （object_type=table，object_name=schema.table）：
 * <ul>
 *   <li>{@code tableio.read_count} —— 本周期读操作次数（fetch）；</li>
 *   <li>{@code tableio.write_count} —— 本周期写操作次数（insert/update/delete）；</li>
 *   <li>{@code tableio.wait_ms} —— 本周期表 I/O 等待耗时（毫秒）。</li>
 * </ul>
 * 回答"哪张表最热、时间花在谁身上"，是等待事件大类之下的对象级下钻。
 * 保留上限供管理端分页查询（默认每页 10 条）。
 *
 * <p><b>天级</b>：{@code table_io_waits_summary_by_index_usage} 扫描疑似未使用索引
 * （COUNT_STAR=0 且非 PRIMARY，排除系统库），产出文本指标
 * {@code mysql.index.unused_list}（JSON，最多 {@value #UNUSED_INDEX_LIMIT} 条）。
 * 注意：P_S 计数自实例启动累计，实例运行时间较短时"未使用"结论不可靠，
 * 采集时一并输出 uptime 天数供页面提示。
 *
 * <p>5.6 无稳定能力口径（与等待事件一致），跳过；首轮建基线不产出。
 */
@Component
public class TableIoStatItem implements MySqlMetricItem {

    public static final String CODE = "table_io_stat";

    private static final double PS_TO_MS = 1_000_000_000.0;
    /** 小时级热点表保留上限（按 wait_ms 降序），支撑管理端真实分页。 */
    private static final int TOP_N = 200;
    /** 天级疑似未使用索引保留上限，支撑管理端真实分页。 */
    private static final int UNUSED_INDEX_LIMIT = 500;

    private static final String SYSTEM_SCHEMAS =
            "'mysql','sys','performance_schema','information_schema'";

    /** instanceId → (schema.table → [countRead, countWrite, sumTimerPs])。 */
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
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        if (!adapter.supportsPerformanceSchema()) {
            return;
        }
        if (request.getFrequency() == CollectFrequency.HOURLY) {
            collectTableIo(conn, request.getInstanceId(), sink);
        } else if (request.getFrequency() == CollectFrequency.DAILY) {
            collectUnusedIndexes(conn, sink);
        }
    }

    // ---- 小时级：表 I/O 热点（差值 Top N）----

    private void collectTableIo(Connection conn, long instanceId, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();

        Map<String, long[]> current = new HashMap<>();
        String sql = "SELECT OBJECT_SCHEMA, OBJECT_NAME, COUNT_READ, COUNT_WRITE, SUM_TIMER_WAIT "
                + "FROM performance_schema.table_io_waits_summary_by_table "
                + "WHERE COUNT_STAR > 0 AND OBJECT_SCHEMA NOT IN (" + SYSTEM_SCHEMAS + ")";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String schema = rs.getString(1);
                    String table = rs.getString(2);
                    if (schema == null || table == null) {
                        continue;
                    }
                    current.put(schema + "." + table,
                            new long[]{rs.getLong(3), rs.getLong(4), rs.getLong(5)});
                }
            }
        }

        Map<String, long[]> prev = baselines.put(instanceId, current);
        if (prev == null) {
            return;
        }

        record Delta(String name, long read, long write, double waitMs) {
        }
        List<Delta> deltas = new ArrayList<>();
        for (Map.Entry<String, long[]> e : current.entrySet()) {
            long[] cur = e.getValue();
            long[] base = prev.get(e.getKey());
            long read = base == null || cur[0] < base[0] ? cur[0] : cur[0] - base[0];
            long write = base == null || cur[1] < base[1] ? cur[1] : cur[1] - base[1];
            long timer = base == null || cur[2] < base[2] ? cur[2] : cur[2] - base[2];
            if (read == 0 && write == 0 && timer == 0) {
                continue;
            }
            deltas.add(new Delta(e.getKey(), read, write, round2(timer / PS_TO_MS)));
        }
        deltas.sort(Comparator.comparingDouble(d -> -d.waitMs()));

        int limit = Math.min(deltas.size(), TOP_N);
        for (int i = 0; i < limit; i++) {
            Delta d = deltas.get(i);
            sink.addObject("tableio.read_count", "table", d.name(), d.read(), ts);
            sink.addObject("tableio.write_count", "table", d.name(), d.write(), ts);
            sink.addObject("tableio.wait_ms", "table", d.name(), d.waitMs(), ts);
        }
    }

    // ---- 天级：疑似未使用索引 ----

    private void collectUnusedIndexes(Connection conn, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();

        // uptime 天数：P_S 计数自启动累计，运行时间太短时"未使用"不可靠
        long uptimeDays = 0;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS LIKE 'Uptime'")) {
                if (rs.next()) {
                    uptimeDays = rs.getLong(2) / 86400;
                }
            }
        }

        List<String> rows = new ArrayList<>();
        String sql = "SELECT OBJECT_SCHEMA, OBJECT_NAME, INDEX_NAME "
                + "FROM performance_schema.table_io_waits_summary_by_index_usage "
                + "WHERE INDEX_NAME IS NOT NULL AND INDEX_NAME <> 'PRIMARY' "
                + "AND COUNT_STAR = 0 "
                + "AND OBJECT_SCHEMA NOT IN (" + SYSTEM_SCHEMAS + ") "
                + "ORDER BY OBJECT_SCHEMA, OBJECT_NAME, INDEX_NAME "
                + "LIMIT " + UNUSED_INDEX_LIMIT;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
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
        sink.addText("mysql.index.unused_list", json, ts);
    }

    /** 实例删除/暂停时清理基线。 */
    public void evict(long instanceId) {
        baselines.remove(instanceId);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
