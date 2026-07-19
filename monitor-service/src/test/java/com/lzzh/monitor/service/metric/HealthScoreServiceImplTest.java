package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadata;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthScoreServiceImplTest {

    @Test
    void calculatesSqlServerHealthWithUnderstandableBackupAndHaDeductions() {
        TsMetricLatestDao latestDao = mock(TsMetricLatestDao.class);
        InstanceRuntimeMetadataService metadata = mock(InstanceRuntimeMetadataService.class);
        HealthScoreServiceImpl service = new HealthScoreServiceImpl();
        ReflectionTestUtils.setField(service, "latestDao", latestDao);
        ReflectionTestUtils.setField(service, "runtimeMetadataService", metadata);
        when(metadata.getRequired(2L)).thenReturn(new InstanceRuntimeMetadata(
                2L, 10L, 20L, "SQLSERVER", "SQL Server", "2022", null, null));
        when(latestDao.latestFrom1m(eq(2L), anySet())).thenReturn(java.util.Map.of(
                "sqlserver.availability", 1.0, "sqlserver.ag.disconnected_replicas", 1.0,
                "sqlserver.blocked_sessions", 2.0));
        when(latestDao.latestFrom1h(eq(2L), anySet())).thenReturn(java.util.Map.of(
                "sqlserver.backup.uncovered_database_count", 1.0));

        var result = service.calculate(2L);
        assertThat(result.getScore()).isLessThan(100);
        assertThat(result.getDeductions()).extracting("message")
                .anyMatch(v -> String.valueOf(v).contains("断连副本"))
                .anyMatch(v -> String.valueOf(v).contains("未纳入完整备份"));
    }

    @Test
    void calculatesSqlServerSecurityDimensionFromDailyAuditMetrics() {
        TsMetricLatestDao latestDao = mock(TsMetricLatestDao.class);
        InstanceRuntimeMetadataService metadata = mock(InstanceRuntimeMetadataService.class);
        HealthScoreServiceImpl service = new HealthScoreServiceImpl();
        ReflectionTestUtils.setField(service, "latestDao", latestDao);
        ReflectionTestUtils.setField(service, "runtimeMetadataService", metadata);
        when(metadata.getRequired(3L)).thenReturn(new InstanceRuntimeMetadata(
                3L, 10L, 20L, "SQLSERVER", "SQL Server", "2022", null, null));
        when(latestDao.latestFrom1m(eq(3L), anySet())).thenReturn(java.util.Map.of(
                "sqlserver.availability", 1.0));
        when(latestDao.latestFrom1h(eq(3L), anySet())).thenReturn(java.util.Map.of());
        when(latestDao.latestFrom1d(eq(3L), anySet())).thenReturn(java.util.Map.of(
                "sqlserver.security.policy_disabled_login_count", 2.0,
                "sqlserver.security.expiration_disabled_login_count", 3.0,
                "sqlserver.security.sa_enabled", 1.0,
                "sqlserver.security.enabled_sysadmin_login_count", 4.0,
                "sqlserver.security.trustworthy_database_count", 1.0,
                "sqlserver.security.db_chaining_database_count", 0.0));

        var result = service.calculate(3L);

        assertThat(result.getDimensions())
                .filteredOn(dimension -> "security".equals(dimension.getDimension()))
                .singleElement()
                .satisfies(dimension -> assertThat(dimension.getScore()).isEqualTo(27));
        assertThat(result.getDeductions())
                .filteredOn(deduction -> "security".equals(deduction.getDimension()))
                .extracting("message")
                .anyMatch(value -> String.valueOf(value).contains("sa"))
                .anyMatch(value -> String.valueOf(value).contains("TRUSTWORTHY"));
    }
    @Test
    void rejectsUnknownDatabaseTypeFromRuntimeMetadataInsteadOfFallingBackToMySql() {
        TsMetricLatestDao latestDao = mock(TsMetricLatestDao.class);
        InstanceRuntimeMetadataService runtimeMetadataService = mock(InstanceRuntimeMetadataService.class);
        HealthScoreServiceImpl service = new HealthScoreServiceImpl();
        ReflectionTestUtils.setField(service, "latestDao", latestDao);
        ReflectionTestUtils.setField(service, "runtimeMetadataService", runtimeMetadataService);
        when(runtimeMetadataService.getRequired(1L)).thenReturn(new InstanceRuntimeMetadata(
                1L, 10L, 20L, "ORACLE", "Oracle", "19c", null, null));

        assertThatThrownBy(() -> service.calculate(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ORACLE");
    }
}
