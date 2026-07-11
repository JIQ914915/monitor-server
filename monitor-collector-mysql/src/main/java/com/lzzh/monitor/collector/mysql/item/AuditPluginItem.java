package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 采集项：审计插件对接（§15.4.5 完整审计，天级，全版本通用）。
 *
 * <p>平台内置的语句审计（StatementAuditItem）是"轻审计"——能发现发生了什么，
 * 但无法还原是谁在哪个客户端执行的。完整审计需依赖数据库侧审计插件，本采集项负责
 * <b>探测并对接主流审计插件的启用状态与关键配置</b>：
 * <ul>
 *   <li>MySQL Enterprise Audit / Percona Audit Log Plugin —— 插件名 {@code audit_log}；</li>
 *   <li>MariaDB Audit Plugin（可装在 MySQL 上）—— 插件名 {@code SERVER_AUDIT}。</li>
 * </ul>
 *
 * 产出指标：
 * <ul>
 *   <li>{@code mysql.security.audit_plugin_active} —— 审计插件是否已安装并处于 ACTIVE（1/0）；</li>
 *   <li>{@code mysql.security.audit_plugin_info}（文本）—— 详情 JSON：
 *       {@code {plugin, status, vars:{...}}}，vars 为该插件的策略/输出/文件等关键配置，
 *       供安全页展示与配置巡检（例如 audit_log_policy=LOGINS 时提示未覆盖查询审计）。</li>
 * </ul>
 * 审计日志本体写在数据库服务器本地文件（或 syslog），平台不远程读取文件内容；
 * 未安装插件时页面以"关注"状态展示并给出安装引导，轻审计继续兜底。
 */
@Component
public class AuditPluginItem implements MySqlMetricItem {

    public static final String CODE = "audit_plugin";

    /** 各插件需要采集的配置变量前缀。 */
    private static final Map<String, String> PLUGIN_VAR_PREFIX = Map.of(
            "audit_log", "audit_log_",
            "server_audit", "server_audit_");

    /** 每个插件最多带出的关键变量（避免文本指标过大）。 */
    private static final Set<String> WANTED_VARS = Set.of(
            // MySQL Enterprise / Percona audit_log
            "audit_log_policy", "audit_log_format", "audit_log_file",
            "audit_log_handler", "audit_log_strategy", "audit_log_rotate_on_size",
            // MariaDB server_audit
            "server_audit_logging", "server_audit_events", "server_audit_output_type",
            "server_audit_file_path", "server_audit_excl_users", "server_audit_incl_users");

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();

        String pluginName = null;
        String pluginStatus = null;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW PLUGINS")) {
                while (rs.next()) {
                    String name = rs.getString("Name");
                    if (name == null) {
                        continue;
                    }
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (PLUGIN_VAR_PREFIX.containsKey(lower)) {
                        pluginName = lower;
                        pluginStatus = rs.getString("Status");
                        break;
                    }
                }
            }
        }

        boolean active = pluginName != null && "ACTIVE".equalsIgnoreCase(pluginStatus);
        sink.addNumeric("mysql.security.audit_plugin_active", active ? 1 : 0, ts);

        Map<String, String> vars = new LinkedHashMap<>();
        if (pluginName != null) {
            String prefix = PLUGIN_VAR_PREFIX.get(pluginName);
            try (Statement st = conn.createStatement()) {
                st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
                try (ResultSet rs = st.executeQuery(
                        "SHOW GLOBAL VARIABLES LIKE '" + prefix + "%'")) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && WANTED_VARS.contains(name.toLowerCase(Locale.ROOT))) {
                            vars.put(name.toLowerCase(Locale.ROOT), rs.getString(2));
                        }
                    }
                }
            } catch (SQLException e) {
                // 变量查询失败不影响插件状态本身的上报
            }
        }

        sink.addText("mysql.security.audit_plugin_info", buildJson(pluginName, pluginStatus, vars), ts);
    }

    private static String buildJson(String plugin, String status, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"plugin\":").append(plugin == null ? "null" : "\"" + escape(plugin) + "\"")
          .append(",\"status\":").append(status == null ? "null" : "\"" + escape(status) + "\"")
          .append(",\"vars\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"")
              .append(escape(e.getValue() == null ? "" : e.getValue())).append('"');
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
