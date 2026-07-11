package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：权限变更 / 危险操作审计（§15.4.3 二期，分钟级，5.7/8.0）。
 *
 * <p>基于 {@code performance_schema.events_statements_summary_by_digest} 的语句指纹计数差值，
 * 识别本采集周期内新执行的敏感语句：
 * <ul>
 *   <li><b>权限变更</b>：GRANT / REVOKE / CREATE USER / DROP USER / ALTER USER /
 *       RENAME USER / SET PASSWORD → {@code mysql.security.priv_change_delta}；</li>
 *   <li><b>危险操作</b>：DROP TABLE / DROP DATABASE / TRUNCATE，以及不带 WHERE 的
 *       DELETE / UPDATE（整表批量修改）→ {@code mysql.security.dangerous_op_delta}。</li>
 * </ul>
 * 同时产出文本指标 {@code mysql.security.audit_events}：本周期命中语句明细 JSON
 * {@code [{"type","text","schema","count","lastSeen"}]}（最多 20 条），供安全页表格展示。
 *
 * <p>说明：这是"轻审计"——基于指纹计数只能知道"执行过、执行了几次"，
 * 无法还原执行账号与来源 IP（完整审计需 audit 插件或 general log，属项目化选装）。
 * 首轮建基线不产出（避免把历史存量语句误报为新发生）；digest 表被 TRUNCATE 后
 * 以当前计数为增量。5.6 无语句摘要能力，跳过。
 */
@Component
public class StatementAuditItem implements MySqlMetricItem {

    public static final String CODE = "statement_audit";

    private static final int MAX_DETAIL = 20;
    private static final int MAX_TEXT_LEN = 300;

    /** 权限变更语句前缀（digest_text 大写规范形式）。 */
    private static final String[] PRIV_PREFIXES = {
            "GRANT", "REVOKE", "CREATE USER", "DROP USER", "ALTER USER",
            "RENAME USER", "SET PASSWORD"
    };

    /** 危险操作语句前缀。 */
    private static final String[] DANGER_PREFIXES = {
            "DROP TABLE", "DROP DATABASE", "DROP SCHEMA", "TRUNCATE"
    };

    /** instanceId → (digest → 上轮 COUNT_STAR)。 */
    private final Map<Long, Map<String, Long>> baselines = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        if (!adapter.supportsPerformanceSchema()) {
            return;
        }
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();

        // 候选面收窄到 SQL 侧：只取以敏感关键字开头、或 DELETE/UPDATE 语句
        String sql = "SELECT SCHEMA_NAME, DIGEST, DIGEST_TEXT, COUNT_STAR, LAST_SEEN "
                + "FROM performance_schema.events_statements_summary_by_digest "
                + "WHERE DIGEST_TEXT IS NOT NULL AND ("
                + "  DIGEST_TEXT LIKE 'GRANT %' OR DIGEST_TEXT LIKE 'REVOKE %' "
                + "  OR DIGEST_TEXT LIKE 'CREATE USER %' OR DIGEST_TEXT LIKE 'DROP USER %' "
                + "  OR DIGEST_TEXT LIKE 'ALTER USER %' OR DIGEST_TEXT LIKE 'RENAME USER %' "
                + "  OR DIGEST_TEXT LIKE 'SET PASSWORD%' "
                + "  OR DIGEST_TEXT LIKE 'DROP TABLE %' OR DIGEST_TEXT LIKE 'DROP DATABASE %' "
                + "  OR DIGEST_TEXT LIKE 'DROP SCHEMA %' OR DIGEST_TEXT LIKE 'TRUNCATE %' "
                + "  OR ((DIGEST_TEXT LIKE 'DELETE %' OR DIGEST_TEXT LIKE 'UPDATE %') "
                + "      AND DIGEST_TEXT NOT LIKE '% WHERE %')"
                + ")";

        Map<String, Long> current = new HashMap<>();
        Map<String, Object[]> meta = new HashMap<>();   // digest → [type, text, schema, lastSeen, countStar]
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String digest = rs.getString("DIGEST");
                    String text = rs.getString("DIGEST_TEXT");
                    if (digest == null || text == null) {
                        continue;
                    }
                    String type = classify(text);
                    if (type == null) {
                        continue;
                    }
                    long count = rs.getLong("COUNT_STAR");
                    current.put(digest, count);
                    meta.put(digest, new Object[]{
                            type, text, rs.getString("SCHEMA_NAME"), rs.getString("LAST_SEEN"), count});
                }
            }
        }

        Map<String, Long> prev = baselines.put(instanceId, current);
        if (prev == null) {
            // 首轮建基线：历史存量语句不视为"本周期发生"
            return;
        }

        long privDelta = 0;
        long dangerDelta = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, Long> e : current.entrySet()) {
            Long base = prev.get(e.getKey());
            // digest 表 TRUNCATE 后计数重置：以当前值作为增量
            long delta = base == null || e.getValue() < base ? e.getValue() : e.getValue() - base;
            if (delta <= 0) {
                continue;
            }
            Object[] m = meta.get(e.getKey());
            String type = (String) m[0];
            if ("priv".equals(type)) {
                privDelta += delta;
            } else {
                dangerDelta += delta;
            }
            if (details.size() < MAX_DETAIL) {
                String text = truncate((String) m[1]);
                details.add("{\"type\":\"" + type + "\",\"text\":\"" + escape(text)
                        + "\",\"schema\":\"" + escape(nvl((String) m[2]))
                        + "\",\"count\":" + delta
                        + ",\"lastSeen\":\"" + escape(nvl((String) m[3])) + "\"}");
            }
        }

        sink.addNumeric("mysql.security.priv_change_delta", privDelta, ts);
        sink.addNumeric("mysql.security.dangerous_op_delta", dangerDelta, ts);
        if (!details.isEmpty()) {
            sink.addText("mysql.security.audit_events", "[" + String.join(",", details) + "]", ts);
        }
    }

    /**
     * 语句分类：priv=权限变更，danger=危险操作，null=不关注。
     * DELETE/UPDATE 仅在不含 WHERE 时视为危险（整表批量修改）。
     */
    static String classify(String digestText) {
        String upper = digestText.toUpperCase(Locale.ROOT);
        for (String p : PRIV_PREFIXES) {
            if (upper.startsWith(p)) {
                return "priv";
            }
        }
        for (String p : DANGER_PREFIXES) {
            if (upper.startsWith(p)) {
                return "danger";
            }
        }
        if ((upper.startsWith("DELETE") || upper.startsWith("UPDATE")) && !upper.contains(" WHERE ")) {
            return "danger";
        }
        return null;
    }

    /** 实例删除/暂停时清理基线。 */
    public void evict(long instanceId) {
        baselines.remove(instanceId);
    }

    private static String truncate(String s) {
        return s.length() <= MAX_TEXT_LEN ? s : s.substring(0, MAX_TEXT_LEN) + "...";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
