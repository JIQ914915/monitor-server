package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：账号安全巡检（§P2-1，天级）。
 *
 * <p>巡检维度：
 * <ul>
 *   <li>{@code mysql.security.empty_password_count}：空/null 密码账号数</li>
 *   <li>{@code mysql.security.any_host_account_count}：Host='%' 的账号数（高风险宽泛授权）</li>
 *   <li>{@code mysql.security.super_priv_count}：拥有 SUPER 权限的账号数</li>
 *   <li>{@code mysql.security.anonymous_user_count}：匿名账号数（User=''）</li>
 * </ul>
 *
 * <p>依赖：采集账号需具备 {@code mysql.user} 的 SELECT 权限（或 {@code SELECT} 全局权限）。
 * 无权限时各指标将因 SQL 异常被跳过，由 item 级异常机制上报。
 */
@Component
public class SecurityAuditItem implements MySqlMetricItem {

    private static final String CODE = "security_audit";
    private static final int QUERY_TIMEOUT_SECONDS = 10;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();

        collectCount(conn, "mysql.security.empty_password_count",
                "SELECT COUNT(*) FROM mysql.user "
                + "WHERE (authentication_string = '' OR authentication_string IS NULL) "
                + "AND account_locked = 'N'",
                sink, ts);

        collectCount(conn, "mysql.security.any_host_account_count",
                "SELECT COUNT(*) FROM mysql.user WHERE Host = '%' AND User != ''",
                sink, ts);

        collectCount(conn, "mysql.security.super_priv_count",
                "SELECT COUNT(*) FROM mysql.user WHERE Super_priv = 'Y' AND User != ''",
                sink, ts);

        collectCount(conn, "mysql.security.anonymous_user_count",
                "SELECT COUNT(*) FROM mysql.user WHERE User = ''",
                sink, ts);
    }

    private void collectCount(Connection conn, String metric, String sql,
                               MetricSink sink, long ts) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    sink.addNumeric(metric, rs.getLong(1), ts);
                }
            }
        }
    }
}
