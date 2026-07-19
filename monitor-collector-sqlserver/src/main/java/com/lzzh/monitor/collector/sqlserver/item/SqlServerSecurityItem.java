package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.sqlserver.version.SqlServerVersionAdapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;

/** SQL Server 天级安全基线巡检；权限不足时不产出零值，避免形成虚假满分。 */
@Component
public class SqlServerSecurityItem implements SqlServerMetricItem {
    @Override
    public String code() {
        return "security";
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, SqlServerVersionAdapter adapter,
                        SqlServerMetricSink sink) throws Exception {
        long ts = System.currentTimeMillis();
        try (Statement statement = conn.createStatement()) {
            statement.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = statement.executeQuery(adapter.securityAuditSql())) {
                if (!rs.next()) {
                    sink.addItemError(code(), "SQL Server 安全巡检未返回数据");
                    return;
                }
                if (rs.getInt("can_view_any_definition") != 1) {
                    sink.addItemError(code(), "监控账号缺少 VIEW ANY DEFINITION，无法完整检查登录账号安全配置");
                    return;
                }
                emit(sink, "sqlserver.security.policy_disabled_login_count", rs, "policy_disabled_login_count", ts);
                emit(sink, "sqlserver.security.expiration_disabled_login_count", rs, "expiration_disabled_login_count", ts);
                emit(sink, "sqlserver.security.sa_enabled", rs, "sa_enabled", ts);
                emit(sink, "sqlserver.security.enabled_sysadmin_login_count", rs, "enabled_sysadmin_login_count", ts);
                emit(sink, "sqlserver.security.trustworthy_database_count", rs, "trustworthy_database_count", ts);
                emit(sink, "sqlserver.security.db_chaining_database_count", rs, "db_chaining_database_count", ts);
            }
        }
    }

    private static void emit(SqlServerMetricSink sink, String metricCode, ResultSet rs,
                             String column, long ts) throws Exception {
        sink.addNumeric(metricCode, rs.getDouble(column), ts);
    }
}