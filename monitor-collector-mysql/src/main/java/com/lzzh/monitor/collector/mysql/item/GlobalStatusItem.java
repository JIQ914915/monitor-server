package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：SHOW GLOBAL STATUS（§7.3 / §9.3）。
 * <p>区分两类变量并分别处理：
 * <ul>
 *   <li><b>瞬时值（gauge）</b>：如 Threads_connected/Threads_running/Uptime，原样落库为 mysql.status.&lt;name&gt;；</li>
 *   <li><b>累积计数器（counter）</b>：如 Questions、Com_xxx、Bytes_xxx、Innodb_rows_xxx、Slow_queries，
 *       经 {@link CounterDeltaStore} 做差值并按实际时间间隔换算为每秒速率，落库为 mysql.rate.&lt;name&gt;；
 *       首次采样或计数器回绕时跳过速率点（不产生负值/空值）。</li>
 * </ul>
 * QPS/TPS 由 ThroughputItem 专门产出；此处 counter 速率覆盖更广的负载/IO 维度。
 */
@Component
public class GlobalStatusItem implements MySqlMetricItem {

    public static final String CODE = "global_status";

    /** 瞬时值：原样落库。 */
    private static final Set<String> GAUGES = Set.of(
            "Threads_connected", "Threads_running", "Uptime",
            // 表缓存（15.4.2 二期）
            "Open_tables", "Open_table_definitions",
            // 查询缓存（仅 5.6/5.7 存在，8.0 已移除该状态变量，自然不产出）
            "Qcache_free_memory", "Qcache_queries_in_cache"
    );

    /** 累积计数器：做差值 → 每秒速率。 */
    private static final Set<String> COUNTERS = Set.of(
            "Threads_created",
            "Questions", "Queries", "Slow_queries",
            "Com_select", "Com_insert", "Com_update", "Com_delete",
            "Com_commit", "Com_rollback",
            "Bytes_received", "Bytes_sent",
            "Innodb_buffer_pool_read_requests", "Innodb_buffer_pool_reads",
            "Innodb_buffer_pool_pages_flushed",       // Buffer Pool 刷页速率（页/秒）
            "Innodb_rows_read", "Innodb_rows_inserted", "Innodb_rows_updated", "Innodb_rows_deleted",
            "Aborted_connects", "Connection_errors_max_connections",
            "Created_tmp_disk_tables", "Created_tmp_tables", "Created_tmp_files",
            "Sort_scan", "Sort_range", "Sort_merge_passes",
            "Table_open_cache_misses",
            // 二期 Phase 1：Handler 统计（索引读 vs 顺序扫描读，索引使用分析）
            "Handler_read_first", "Handler_read_key", "Handler_read_next",
            "Handler_read_rnd", "Handler_read_rnd_next",
            // 二期 Phase 1：Select 类型统计（全表扫描/无索引 JOIN 识别）
            "Select_scan", "Select_full_join", "Select_full_range_join", "Select_range",
            // 表缓存 / 查询缓存 / 表级锁（15.4.1/15.4.2 二期）
            "Opened_tables",
            "Qcache_hits", "Qcache_inserts", "Qcache_lowmem_prunes",
            "Table_locks_waited", "Table_locks_immediate"
    );

    /**
     * 除速率之外，额外输出"本周期增量"的计数器集合。
     * <p>以下指标在告警 / 趋势场景更需要"本次采集间隔内新增了多少"，
     * 而非换算成每秒速率（速率在低频分钟级采集时精度损失较大）：
     * <ul>
     *   <li>Slow_queries：慢查询周期增量，用于慢 SQL 趋势图和告警规则</li>
     *   <li>Created_tmp_tables：内存临时表周期增量，汇总后得"今日临时表数"</li>
     *   <li>Created_tmp_disk_tables：磁盘临时表周期增量，汇总后得"今日磁盘临时表数"</li>
     *   <li>Aborted_connects：连接失败（认证失败/网络中断等）周期增量，用于连接被拒告警</li>
     *   <li>Connection_errors_max_connections：因连接数打满被拒的周期增量，用于连接打满告警</li>
     * </ul>
     */
    private static final Set<String> EXTRA_DELTA = Set.of(
            "Slow_queries",
            "Created_tmp_tables",
            "Created_tmp_disk_tables",
            "Aborted_connects",
            "Connection_errors_max_connections"
    );

    private final CounterDeltaStore deltaStore;

    /**
     * 实例重启检测基线（instanceId → 上轮 Uptime 秒数）。
     * Uptime 相比上轮变小即说明实例在两次采集之间发生过重启。
     * 基线仅存节点内存，Collector 重启后首轮不判定（与其他 delta 基线策略一致）。
     */
    private final ConcurrentHashMap<Long, Long> prevUptime = new ConcurrentHashMap<>();

    public GlobalStatusItem(CounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        long instanceId = request.getInstanceId();

        // 一次扫描全量 SHOW GLOBAL STATUS，同时写缓存供同轮其他采集项（如 ThroughputItem）复用
        Map<String, Long> rawCounters = new HashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name == null) {
                        continue;
                    }
                    String val = rs.getString(2);
                    if (GAUGES.contains(name)) {
                        Double v = parseDouble(val);
                        if (v != null) {
                            sink.addNumeric("mysql.status." + name, v, ts);
                        }
                        // GAUGE 值也进快照：ThroughputItem 依赖快照中的 Uptime 做首采样
                        // "自启动均值" 兜底，缺失会使重启后首轮 QPS/TPS 误报为 0
                        Long rawGauge = parseLong(val);
                        if (rawGauge != null) {
                            rawCounters.put(name, rawGauge);
                            // 连接数为 gauge，但"激增"告警需要周期增量：
                            // 相比上周期下降时 delta 返回 null（复用回绕语义），只在上升时产点
                            if ("Threads_connected".equals(name)) {
                                Long delta = deltaStore.delta(instanceId, "gstatus_delta." + name, rawGauge, ts);
                                if (delta != null) {
                                    sink.addNumeric("mysql.delta.threads_connected", (double) delta, ts);
                                }
                            }
                            // 实例重启检测：Uptime 变小 = 两次采集之间发生过重启
                            if ("Uptime".equals(name)) {
                                Long prev = prevUptime.put(instanceId, rawGauge);
                                if (prev != null) {
                                    sink.addNumeric("mysql.instance.restarted", rawGauge < prev ? 1 : 0, ts);
                                }
                            }
                        }
                    } else if (COUNTERS.contains(name)) {
                        Long raw = parseLong(val);
                        if (raw != null) {
                            rawCounters.put(name, raw);
                            Double rate = deltaStore.rate(instanceId, "gstatus." + name, raw, ts);
                            if (rate != null) {
                                sink.addNumeric("mysql.rate." + name, round2(rate), ts);
                            }
                            // 对 EXTRA_DELTA 中的计数器额外输出本周期增量（delta），
                            // 增量比速率更直观，适合告警规则和趋势图
                            if (EXTRA_DELTA.contains(name)) {
                                Long delta = deltaStore.delta(instanceId, "gstatus_delta." + name, raw, ts);
                                if (delta != null) {
                                    String metricName = "mysql.delta." + name.toLowerCase(Locale.ROOT);
                                    sink.addNumeric(metricName, (double) delta, ts);
                                }
                            }
                        }
                    } else {
                        // 其他数值型状态也入缓存（供 ThroughputItem 等复用）
                        Long raw = parseLong(val);
                        if (raw != null) {
                            rawCounters.put(name, raw);
                        }
                    }
                }
            }
        }
        // 缓存全量计数器快照，供同轮 ThroughputItem 等复用，避免重复全量查询
        sink.setGlobalStatusSnapshot(rawCounters);
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
