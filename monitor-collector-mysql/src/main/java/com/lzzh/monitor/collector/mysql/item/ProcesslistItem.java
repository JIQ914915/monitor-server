package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.LongConnPoint;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集项：Processlist 连接分析（§7.3 连接域，分钟级）。
 *
 * <p>一次 {@code SELECT * FROM information_schema.processlist} 扫描，同时产出：
 * <ul>
 *   <li><b>连接状态分布</b>（数值指标）：Sleep / Query / Locked / Other 各类连接计数；</li>
 *   <li><b>连接来源 Top 20</b>（对象指标，写入 metric_capacity_object）：
 *       按 host+user+db 聚合，记录 total 与 active 连接数；</li>
 *   <li><b>长连接摘要</b>（数值指标）：TIME ≥ 阈值的非 Sleep 连接数 + 最长持续秒数；</li>
 *   <li><b>长连接明细</b>（LongConnPoint，写入 metric_long_conn）：
 *       TIME ≥ {@value LONG_CONN_THRESHOLD_SECONDS} 秒的单条连接快照，
 *       最多保留 {@value MAX_LONG_CONN_RECORDS} 条（按 TIME 降序）。</li>
 * </ul>
 *
 * <p>排除监控连接自身（{@code id = CONNECTION_ID()}），避免监控采集污染连接统计。
 */
@Component
public class ProcesslistItem implements MySqlMetricItem {

    public static final String CODE = "processlist";

    /** 超过此秒数的非 Sleep 连接视为"长连接"。 */
    private static final int LONG_CONN_THRESHOLD_SECONDS = 30;

    /** 来源聚合 Top N 上限（按总连接数降序）。 */
    private static final int SOURCE_TOP_N = 20;

    /** 长连接明细记录上限（按 TIME 降序）。 */
    private static final int MAX_LONG_CONN_RECORDS = 50;

    /** INFO 列截断长度。 */
    private static final int MAX_INFO_LEN = 2000;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();

        // 计数器
        long sleepCount = 0, queryCount = 0, lockedCount = 0, otherCount = 0;
        long longRunningCount = 0, maxDurationSeconds = 0;
        // 线程状态细化（15.4.1 二期）：按 State 关键字分桶
        long sendingData = 0, sorting = 0, tmpTable = 0, copying = 0;

        // 来源聚合：key = "host|user|db"
        Map<String, long[]> sourceMap = new HashMap<>();   // [0]=total, [1]=active

        // 连接来源白名单检测（15.4.3 二期）：白名单为空则不启用
        List<String> whitelist = request.getConnSourceWhitelist();
        boolean guardEnabled = whitelist != null && !whitelist.isEmpty();
        Map<String, long[]> unknownSources = new HashMap<>();   // "host|user|db" → [total]

        // 长连接明细（先收集，后截取 TOP N）
        List<LongConnPoint> longConns = new ArrayList<>();

        String sql = "SELECT id, user, host, db, command, time, state, info "
                + "FROM information_schema.processlist "
                + "WHERE id != CONNECTION_ID() "
                + "ORDER BY time DESC";

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String command = nvl(rs.getString("command"));
                int time     = rs.getInt("time");
                String state = nvl(rs.getString("state"));
                String user  = nvl(rs.getString("user"));
                String host  = nvl(rs.getString("host"));
                String db    = nvl(rs.getString("db"));
                long   id    = rs.getLong("id");
                String info  = rs.getString("info");

                // --- 状态分类 ---
                String cmd = command.toLowerCase();
                boolean isSleep = "sleep".equals(cmd);
                boolean isQuery = "query".equals(cmd) || "execute".equals(cmd);
                boolean isLocked = state.toLowerCase().contains("lock");

                if (isSleep) {
                    sleepCount++;
                } else if (isLocked) {
                    lockedCount++;
                } else if (isQuery) {
                    queryCount++;
                } else {
                    otherCount++;
                }

                // --- 线程状态细化（活跃线程按 State 关键字分桶，桶间不互斥不求和）---
                if (!isSleep) {
                    String stateLower = state.toLowerCase();
                    if (stateLower.contains("sending data") || stateLower.contains("executing")) {
                        sendingData++;
                    }
                    if (stateLower.contains("sort")) {
                        sorting++;
                    }
                    if (stateLower.contains("tmp table") || stateLower.contains("temporary")) {
                        tmpTable++;
                    }
                    if (stateLower.contains("copy")) {
                        copying++;
                    }
                }

                // --- 来源聚合 ---
                // 取 host 中的 IP 部分（host:port → host），截断到 128 字符
                String hostIp = hostWithoutPort(host);
                String sourceKey = hostIp + "|" + user + "|" + db;
                long[] cnt = sourceMap.computeIfAbsent(sourceKey, k -> new long[]{0, 0});
                cnt[0]++;                    // total
                if (!isSleep) cnt[1]++;      // active

                // --- 白名单来源检测（本地 socket / localhost 视为可信）---
                if (guardEnabled && !isLocalHost(hostIp) && !matchWhitelist(hostIp, whitelist)) {
                    unknownSources.computeIfAbsent(sourceKey, k -> new long[]{0})[0]++;
                }

                // --- 长连接统计 ---
                if (!isSleep && time >= LONG_CONN_THRESHOLD_SECONDS) {
                    longRunningCount++;
                    if (time > maxDurationSeconds) {
                        maxDurationSeconds = time;
                    }
                    if (longConns.size() < MAX_LONG_CONN_RECORDS) {
                        String infoTrunc = info != null && info.length() > MAX_INFO_LEN
                                ? info.substring(0, MAX_INFO_LEN) : info;
                        longConns.add(new LongConnPoint(id, user, host, db, command, time, state, infoTrunc, ts));
                    }
                }
            }
            }
        }

        // --- 写数值指标：状态分布 ---
        sink.addNumeric("mysql.conn.state.sleep",  (double) sleepCount,  ts);
        sink.addNumeric("mysql.conn.state.query",  (double) queryCount,  ts);
        sink.addNumeric("mysql.conn.state.locked", (double) lockedCount, ts);
        sink.addNumeric("mysql.conn.state.other",  (double) otherCount,  ts);

        // --- 写数值指标：线程状态细化 ---
        sink.addNumeric("mysql.conn.statedetail.sending_data", (double) sendingData, ts);
        sink.addNumeric("mysql.conn.statedetail.sorting",      (double) sorting,     ts);
        sink.addNumeric("mysql.conn.statedetail.tmp_table",    (double) tmpTable,    ts);
        sink.addNumeric("mysql.conn.statedetail.copying",      (double) copying,     ts);

        // --- 写数值指标：长连接摘要 ---
        sink.addNumeric("mysql.conn.long_running_count",    (double) longRunningCount,    ts);
        sink.addNumeric("mysql.conn.max_duration_seconds",  (double) maxDurationSeconds,  ts);

        // --- 写指标：白名单外来源（启用白名单时恒产出，0 值保证趋势/恢复判定连续）---
        if (guardEnabled) {
            long unknownConnTotal = unknownSources.values().stream().mapToLong(a -> a[0]).sum();
            sink.addNumeric("mysql.security.unknown_source_count", (double) unknownConnTotal, ts);
            if (!unknownSources.isEmpty()) {
                StringBuilder sb = new StringBuilder("[");
                int i = 0;
                for (Map.Entry<String, long[]> e : unknownSources.entrySet()) {
                    if (i++ >= 20) {
                        break;
                    }
                    String[] parts = e.getKey().split("\\|", -1);
                    if (sb.length() > 1) {
                        sb.append(',');
                    }
                    sb.append("{\"host\":\"").append(jsonEscape(parts[0]))
                            .append("\",\"user\":\"").append(jsonEscape(parts.length > 1 ? parts[1] : ""))
                            .append("\",\"db\":\"").append(jsonEscape(parts.length > 2 ? parts[2] : ""))
                            .append("\",\"total\":").append(e.getValue()[0]).append('}');
                }
                sink.addText("mysql.security.unknown_sources", sb.append(']').toString(), ts);
            }
        }

        // --- 写对象指标：来源 Top N ---
        sourceMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(SOURCE_TOP_N)
                .forEach(e -> {
                    String key = e.getKey();
                    long[] c   = e.getValue();
                    sink.addObject("conn.source.total",  "conn_source", key, (double) c[0], ts);
                    sink.addObject("conn.source.active", "conn_source", key, (double) c[1], ts);
                });

        // --- 写长连接明细 ---
        longConns.forEach(sink::addLongConn);
    }

    /** 本地连接（socket / localhost / 127.0.0.1 / ::1）视为可信，不做白名单比对。 */
    private static boolean isLocalHost(String hostIp) {
        return hostIp.isEmpty() || "localhost".equalsIgnoreCase(hostIp)
                || "127.0.0.1".equals(hostIp) || "::1".equals(hostIp);
    }

    /** 白名单匹配：精确相等，或条目以 "*" 结尾时按前缀匹配（如 "10.0.1.*"）。 */
    static boolean matchWhitelist(String hostIp, List<String> whitelist) {
        for (String entry : whitelist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String e = entry.trim();
            if (e.endsWith("*")) {
                if (hostIp.startsWith(e.substring(0, e.length() - 1))) {
                    return true;
                }
            } else if (hostIp.equalsIgnoreCase(e)) {
                return true;
            }
        }
        return false;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 去掉 host:port 中的端口部分，仅保留 IP/hostname。 */
    private static String hostWithoutPort(String host) {
        if (host == null || host.isBlank()) return "";
        int colon = host.lastIndexOf(':');
        return colon > 0 ? host.substring(0, colon) : host;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
