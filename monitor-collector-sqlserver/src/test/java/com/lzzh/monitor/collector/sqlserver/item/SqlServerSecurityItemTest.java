package com.lzzh.monitor.collector.sqlserver.item;

import com.lzzh.monitor.collector.sqlserver.version.SqlServer2017Adapter;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlServerSecurityItemTest {

    @Test
    void emitsDailySecurityMetricsWhenMetadataPermissionIsAvailable() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(new SqlServer2017Adapter().securityAuditSql())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("can_view_any_definition")).thenReturn(1);
        when(resultSet.getDouble("policy_disabled_login_count")).thenReturn(2.0);
        when(resultSet.getDouble("expiration_disabled_login_count")).thenReturn(3.0);
        when(resultSet.getDouble("sa_enabled")).thenReturn(1.0);
        when(resultSet.getDouble("enabled_sysadmin_login_count")).thenReturn(4.0);
        when(resultSet.getDouble("trustworthy_database_count")).thenReturn(1.0);
        when(resultSet.getDouble("db_chaining_database_count")).thenReturn(0.0);

        SqlServerMetricSink sink = new SqlServerMetricSink();
        SqlServerSecurityItem item = new SqlServerSecurityItem();
        item.collect(connection, null, new SqlServer2017Adapter(), sink);

        assertThat(item.frequencies()).containsExactly(CollectFrequency.DAILY);
        assertThat(sink.errors()).isEmpty();
        assertThat(sink.numeric()).extracting("metric").containsExactlyInAnyOrder(
                "sqlserver.security.policy_disabled_login_count",
                "sqlserver.security.expiration_disabled_login_count",
                "sqlserver.security.sa_enabled",
                "sqlserver.security.enabled_sysadmin_login_count",
                "sqlserver.security.trustworthy_database_count",
                "sqlserver.security.db_chaining_database_count");
        verify(statement).setQueryTimeout(SqlServerMetricItem.DEFAULT_QUERY_TIMEOUT_SECONDS);
    }

    @Test
    void reportsCapabilityErrorAndDoesNotEmitFalseZerosWithoutPermission() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(new SqlServer2017Adapter().securityAuditSql())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("can_view_any_definition")).thenReturn(0);

        SqlServerMetricSink sink = new SqlServerMetricSink();
        new SqlServerSecurityItem().collect(connection, null, new SqlServer2017Adapter(), sink);

        assertThat(sink.numeric()).isEmpty();
        assertThat(sink.errors()).singleElement()
                .satisfies(error -> assertThat(error.message()).contains("VIEW ANY DEFINITION"));
    }
}