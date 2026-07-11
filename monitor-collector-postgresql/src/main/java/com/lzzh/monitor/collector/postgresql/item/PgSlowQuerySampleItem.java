package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.SlowSqlSamplePoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：PG 慢 SQL 样本（二期 B3，分钟级，pg_stat_activity 运行中采样）。
 *
 * <p>抓取运行中已超过阈值的活跃语句（真实 SQL 文本 + 用户/来源/等待事件 + 已运行时长），
 * 落既有 metric_slow_sql_sample 表。与 MySQL 的差异：PG 不读慢日志文件
 * （log_min_duration_statement 仅作配置巡检提示），改为对 pg_stat_activity 周期采样——
 * 语义是"抓到过它慢"的抽样而非全量，采样间隔内完成的短慢语句可能漏采。
 *
 * <p>阈值：优先使用目标库 log_min_duration_statement（>0 时，毫秒）；
 * 未开启（-1/0）时按默认 {@value #DEFAULT_THRESHOLD_MS} ms。
 *
 * <p>去重：同一次执行（pid + query_start）只采一次；一次执行跨多个采集周期时，
 * 后续周期跳过（首个命中周期已记录文本，时长以命中时刻为准）。
 *
 * <p>字段映射：thread_id ← pid；event_id ← query_start 毫秒时间戳；
 * digest ← pg_stat_activity.query_id（PG14+，13 为 null）；exec_time_us ← 已运行时长（微秒）。
 */
@Component
public class PgSlowQuerySampleItem implements PgMetricItem {

    public static final String CODE = "pg_slow_sql_sample";

    /** log_min_duration_statement 未开启时的默认慢阈值（毫秒）。 */
    private static final long DEFAULT_THRESHOLD_MS = 5000;

    private static final int MAX_SAMPLES_PER_ROUND = 50;
    private static final int MAX_SQL_LEN = 65536;

    /** 每实例已采样执行的水位（pid:query_start_ms → 见过），容量有限防泄漏。 */
    private final Map<Long, Map<String, Long>> seen = new ConcurrentHashMap<>();
    private static final int MAX_SEEN_PER_INSTANCE = 2000;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.MINUTE);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();
        long thresholdMs = resolveThresholdMs(conn);
        boolean hasQueryId = majorVersion(request.getVersion()) >= 14;

        String sql = "SELECT pid, usename, client_addr::text AS client_addr, datname, "
                + (hasQueryId ? "query_id, " : "NULL::bigint AS query_id, ")
                + "wait_event_type, wait_event, "
                + "extract(epoch FROM query_start) * 1000 AS query_start_ms, "
                + "extract(epoch FROM (now() - query_start)) * 1000000 AS elapsed_us, "
                + "query "
                + "FROM pg_stat_activity "
                + "WHERE state = 'active' "
                + "  AND backend_type = 'client backend' "
                + "  AND pid <> pg_backend_pid() "
                + "  AND query_start IS NOT NULL "
                + "  AND now() - query_start >= make_interval(secs => ? / 1000.0) "
                + "ORDER BY query_start "
                + "LIMIT " + MAX_SAMPLES_PER_ROUND;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            ps.setLong(1, thresholdMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long pid = rs.getLong("pid");
                    long queryStartMs = (long) rs.getDouble("query_start_ms");
                    if (!markNew(instanceId, pid, queryStartMs)) {
                        continue;
                    }
                    String queryText = rs.getString("query");
                    if (queryText == null || queryText.isBlank()) {
                        continue;
                    }
                    if (queryText.length() > MAX_SQL_LEN) {
                        queryText = queryText.substring(0, MAX_SQL_LEN);
                    }
                    long queryId = rs.getLong("query_id");
                    boolean hasDigest = !rs.wasNull() && queryId != 0;
                    String waitEventType = rs.getString("wait_event_type");
                    String waitEvent = rs.getString("wait_event");

                    sink.addSlowSqlSample(new SlowSqlSamplePoint(
                            pid,
                            queryStartMs,
                            rs.getString("usename"),
                            appendWait(rs.getString("client_addr"), waitEventType, waitEvent),
                            rs.getString("datname"),
                            hasDigest ? String.valueOf(queryId) : null,
                            queryText,
                            (long) rs.getDouble("elapsed_us"),
                            0L,
                            0L, 0L, 0L,
                            false, 0L, 0L,
                            ts));
                }
            }
        }
    }

    /** 来源列附加当前等待事件（如 "10.0.0.8 [Lock:transactionid]"），排查时一眼可见卡点。 */
    private static String appendWait(String clientAddr, String waitType, String waitEvent) {
        String addr = clientAddr == null || clientAddr.isBlank() ? "local" : clientAddr;
        if (waitType == null || waitType.isBlank()) {
            return addr;
        }
        return addr + " [" + waitType + (waitEvent == null ? "" : ":" + waitEvent) + "]";
    }

    /** 读取 log_min_duration_statement（毫秒），未开启（<=0）回退默认阈值。 */
    private static long resolveThresholdMs(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery(
                    "SELECT setting::bigint FROM pg_settings WHERE name = 'log_min_duration_statement'")) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    if (v > 0) {
                        return v;
                    }
                }
            }
        } catch (SQLException ignored) {
            // 读取失败按默认阈值
        }
        return DEFAULT_THRESHOLD_MS;
    }

    /** 水位去重：首次见到该 (pid, query_start) 返回 true。超容量时整体重置（最多重复采一轮）。 */
    private boolean markNew(long instanceId, long pid, long queryStartMs) {
        Map<String, Long> m = seen.computeIfAbsent(instanceId, k -> new ConcurrentHashMap<>());
        if (m.size() > MAX_SEEN_PER_INSTANCE) {
            m.clear();
        }
        return m.putIfAbsent(pid + ":" + queryStartMs, queryStartMs) == null;
    }

    private static int majorVersion(String version) {
        if (version == null || version.isBlank()) {
            return 13;
        }
        try {
            String head = version.split("[^0-9]")[0];
            return head.isEmpty() ? 13 : Integer.parseInt(head);
        } catch (Exception e) {
            return 13;
        }
    }
}
