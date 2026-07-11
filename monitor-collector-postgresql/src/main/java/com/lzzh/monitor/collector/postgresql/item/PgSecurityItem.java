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
import java.util.List;
import java.util.Set;

/**
 * 采集项：安全巡检（二期 D3 收尾，天级，对标 MySQL SslStatusItem + SecurityAuditItem）。
 *
 * <ul>
 *   <li>{@code pg.security.ssl_conn_pct} —— 当前客户端连接中走 SSL 加密的百分比
 *       （pg_stat_ssl × pg_stat_activity，仅统计 client backend）；</li>
 *   <li>{@code pg.security.superuser_count} —— 可登录的超级用户角色数（pg_roles，越少越好）；</li>
 *   <li>{@code pg.security.never_expire_count} —— 可登录且密码永不过期的角色数
 *       （rolvaliduntil IS NULL，含正常场景，仅作巡检提示）；</li>
 *   <li>{@code pg.security.roles_info}（文本）—— 角色审计 JSON：超级用户清单、可登录角色总数、
 *       无密码角色清单（读 pg_authid 需超级用户权限，普通监控账号读不到时该字段为 null 并注明）。</li>
 * </ul>
 * 配套体检规则（ssl off 提示）已在参数体检中覆盖，这里补充运行侧占比与账号面数据。
 */
@Component
public class PgSecurityItem implements PgMetricItem {

    public static final String CODE = "pg_security";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();

        // ---- SSL 连接占比（当前时点快照）----
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT COUNT(*)                              AS total,
                           COUNT(*) FILTER (WHERE s.ssl)         AS ssl_count
                      FROM pg_stat_activity a
                      LEFT JOIN pg_stat_ssl s ON s.pid = a.pid
                     WHERE a.backend_type = 'client backend'
                    """)) {
                if (rs.next()) {
                    long total = rs.getLong("total");
                    if (total > 0) {
                        sink.addNumeric("pg.security.ssl_conn_pct",
                                Math.round(rs.getLong("ssl_count") * 10000.0 / total) / 100.0, ts);
                    }
                }
            }
        }

        // ---- 角色审计（pg_roles 全角色可读）----
        long superuserCount = 0;
        long loginRoles = 0;
        long neverExpire = 0;
        List<String> superusers = new ArrayList<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT rolname, rolsuper, rolvaliduntil IS NULL AS never_expire
                      FROM pg_roles
                     WHERE rolcanlogin
                     ORDER BY rolname
                    """)) {
                while (rs.next()) {
                    loginRoles++;
                    if (rs.getBoolean("never_expire")) {
                        neverExpire++;
                    }
                    if (rs.getBoolean("rolsuper")) {
                        superuserCount++;
                        if (superusers.size() < 20) {
                            superusers.add(rs.getString("rolname"));
                        }
                    }
                }
            }
        }
        sink.addNumeric("pg.security.superuser_count", superuserCount, ts);
        sink.addNumeric("pg.security.never_expire_count", neverExpire, ts);

        // ---- 无密码角色（pg_authid 仅超级用户可读，普通监控账号读不到则跳过）----
        List<String> noPassword = null;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(
                    "SELECT rolname FROM pg_authid WHERE rolcanlogin AND rolpassword IS NULL ORDER BY rolname LIMIT 50")) {
                noPassword = new ArrayList<>();
                while (rs.next()) {
                    noPassword.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            // 权限不足属预期情况（pg_monitor 无 pg_authid 读权限），不作为采集失败
        }
        if (noPassword != null) {
            sink.addNumeric("pg.security.nopassword_count", noPassword.size(), ts);
        }

        StringBuilder json = new StringBuilder(256);
        json.append("{\"superusers\":[").append(joinQuoted(superusers))
            .append("],\"loginRoles\":").append(loginRoles)
            .append(",\"neverExpireRoles\":").append(neverExpire);
        if (noPassword == null) {
            json.append(",\"noPasswordRoles\":null,\"noPasswordNote\":\"采集账号无 pg_authid 读权限，未检查\"");
        } else {
            json.append(",\"noPasswordRoles\":[").append(joinQuoted(noPassword)).append(']');
        }
        json.append('}');
        sink.addText("pg.security.roles_info", json.toString(), ts);
    }

    private static String joinQuoted(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(names.get(i))).append('"');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
