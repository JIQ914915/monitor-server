package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 采集项：SSL 连接监控（§15.4.3 二期，天级，全版本通用）。
 *
 * <p>采集实例的传输加密配置状态：
 * <ul>
 *   <li>{@code mysql.security.ssl_enabled} —— SSL 是否启用（have_ssl=YES → 1，否则 0）；</li>
 *   <li>{@code mysql.security.ssl_cert_days_left} —— 服务端证书剩余有效天数
 *       （SHOW GLOBAL STATUS Ssl_server_not_after，5.7+ 且启用 SSL 时有值）；</li>
 *   <li>{@code mysql.security.ssl_info}（文本）—— 配置详情 JSON：
 *       {@code {haveSsl, tlsVersion, requireSecureTransport, certNotAfter}}。</li>
 * </ul>
 * 配套内置规则：证书剩余有效期 ≤ 30 天预警；SSL 未启用不告警（很多内网环境刻意不开），
 * 由安全页以"关注"状态展示。
 */
@Component
public class SslStatusItem implements MySqlMetricItem {

    public static final String CODE = "ssl_status";

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

        Map<String, String> vars = new HashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(
                    "SHOW GLOBAL VARIABLES WHERE Variable_name IN "
                            + "('have_ssl', 'have_openssl', 'tls_version', 'require_secure_transport')")) {
                while (rs.next()) {
                    vars.put(rs.getString(1).toLowerCase(Locale.ROOT), rs.getString(2));
                }
            }
        }

        String certNotAfter = null;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(
                    "SHOW GLOBAL STATUS LIKE 'Ssl_server_not_after'")) {
                if (rs.next()) {
                    certNotAfter = rs.getString(2);
                }
            }
        }

        // 8.0 移除了 have_ssl（恒为内置支持），以 tls_version 非空 + 证书存在为准
        String haveSsl = vars.get("have_ssl");
        boolean enabled;
        if (haveSsl != null) {
            enabled = "YES".equalsIgnoreCase(haveSsl);
        } else {
            enabled = certNotAfter != null && !certNotAfter.isBlank();
        }
        sink.addNumeric("mysql.security.ssl_enabled", enabled ? 1 : 0, ts);

        Long daysLeft = parseDaysLeft(certNotAfter);
        if (daysLeft != null) {
            sink.addNumeric("mysql.security.ssl_cert_days_left", daysLeft, ts);
        }

        String json = "{\"haveSsl\":\"" + escape(nvl(haveSsl, enabled ? "YES" : "DISABLED"))
                + "\",\"tlsVersion\":\"" + escape(nvl(vars.get("tls_version"), ""))
                + "\",\"requireSecureTransport\":\"" + escape(nvl(vars.get("require_secure_transport"), "OFF"))
                + "\",\"certNotAfter\":\"" + escape(nvl(certNotAfter, ""))
                + "\",\"certDaysLeft\":" + (daysLeft == null ? "null" : daysLeft) + "}";
        sink.addText("mysql.security.ssl_info", json, ts);
    }

    /** 解析证书到期时间为剩余天数；无法解析返回 null。 */
    static Long parseDaysLeft(String notAfter) {
        if (notAfter == null || notAfter.isBlank()) {
            return null;
        }
        try {
            // 规范化多空格（MySQL 输出中日期为单数字时会双空格对齐）
            String normalized = notAfter.trim().replaceAll("\\s+", " ");
            LocalDateTime expire = LocalDateTime.parse(normalized,
                    DateTimeFormatter.ofPattern("MMM d HH:mm:ss yyyy z", Locale.ENGLISH));
            return ChronoUnit.DAYS.between(LocalDateTime.now(ZoneOffset.UTC), expire);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nvl(String s, String dft) {
        return s == null ? dft : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
