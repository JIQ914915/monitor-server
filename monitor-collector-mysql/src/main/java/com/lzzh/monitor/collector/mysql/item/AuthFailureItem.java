package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：认证失败来源分析 / 暴力破解检测（§15.4.3 / §15.4.4 二期，分钟级，5.6.5+ 通用）。
 *
 * <p>基于 {@code performance_schema.host_cache} 的按来源 IP 认证错误计数做节点内差值：
 * <ul>
 *   <li>{@code mysql.security.auth_fail_delta} —— 本周期全部来源认证失败（密码错误等）总增量；</li>
 *   <li>{@code mysql.security.brute_force_suspect} —— 暴力破解疑似标记（布尔 1/0）：
 *       单一来源 IP 本周期认证失败 ≥ {@value SINGLE_IP_THRESHOLD} 次，
 *       或全体来源合计 ≥ {@value TOTAL_THRESHOLD} 次（"失败次数 + 来源集中度"复合条件）；</li>
 *   <li>{@code mysql.security.auth_fail_sources}（文本）—— 本周期失败来源 Top 10 明细 JSON
 *       {@code [{"ip","delta","total"}]}。</li>
 * </ul>
 *
 * <p>局限说明：host_cache 只统计非 localhost 的 TCP 连接，且 {@code skip_name_resolve=ON}
 * 或 {@code host_cache_size=0} 时主机缓存被禁用、无数据——此时暴力破解监控退化为已有的
 * {@code mysql.delta.aborted_connects} 全局计数告警。表不存在（极老版本）时静默跳过。
 */
@Component
public class AuthFailureItem implements MySqlMetricItem {

    public static final String CODE = "auth_failure";

    private static final Logger log = LoggerFactory.getLogger(AuthFailureItem.class);

    /** 单一来源 IP 每分钟认证失败次数达到该值即标记疑似暴力破解。 */
    static final int SINGLE_IP_THRESHOLD = 5;

    /** 全体来源合计每分钟认证失败次数达到该值即标记疑似暴力破解。 */
    static final int TOTAL_THRESHOLD = 15;

    private static final int TOP_N = 10;

    /** instanceId → (ip → 上轮 COUNT_AUTHENTICATION_ERRORS)。 */
    private final Map<Long, Map<String, Long>> baselines = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();

        Map<String, Long> current = new HashMap<>();
        String sql = "SELECT IP, COUNT_AUTHENTICATION_ERRORS "
                + "FROM performance_schema.host_cache "
                + "WHERE COUNT_AUTHENTICATION_ERRORS > 0";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String ip = rs.getString(1);
                    if (ip != null) {
                        current.put(ip, rs.getLong(2));
                    }
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146
                    || (e.getMessage() != null && e.getMessage().contains("doesn't exist"))) {
                log.debug("实例 {} 无 performance_schema.host_cache 表，跳过认证失败来源采集", instanceId);
                return;
            }
            throw e;
        }

        Map<String, Long> prev = baselines.put(instanceId, current);
        if (prev == null) {
            // 首轮建基线不产出
            return;
        }

        long total = 0;
        long maxSingle = 0;
        List<String[]> rows = new ArrayList<>();   // [ip, deltaStr, totalStr]
        for (Map.Entry<String, Long> e : current.entrySet()) {
            Long base = prev.get(e.getKey());
            // host_cache 被 FLUSH HOSTS 清空后重新计数：以当前值为增量
            long delta = base == null || e.getValue() < base ? e.getValue() : e.getValue() - base;
            if (delta <= 0) {
                continue;
            }
            total += delta;
            maxSingle = Math.max(maxSingle, delta);
            rows.add(new String[]{e.getKey(), String.valueOf(delta), String.valueOf(e.getValue())});
        }

        sink.addNumeric("mysql.security.auth_fail_delta", total, ts);
        boolean suspect = maxSingle >= SINGLE_IP_THRESHOLD || total >= TOTAL_THRESHOLD;
        sink.addNumeric("mysql.security.brute_force_suspect", suspect ? 1 : 0, ts);

        if (!rows.isEmpty()) {
            rows.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
            StringBuilder sb = new StringBuilder("[");
            int limit = Math.min(rows.size(), TOP_N);
            for (int i = 0; i < limit; i++) {
                String[] r = rows.get(i);
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"ip\":\"").append(escape(r[0]))
                        .append("\",\"delta\":").append(r[1])
                        .append(",\"total\":").append(r[2]).append('}');
            }
            sink.addText("mysql.security.auth_fail_sources", sb.append(']').toString(), ts);
        }
    }

    /** 实例删除/暂停时清理基线。 */
    public void evict(long instanceId) {
        baselines.remove(instanceId);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
