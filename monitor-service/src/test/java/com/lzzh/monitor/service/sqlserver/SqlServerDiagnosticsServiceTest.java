package com.lzzh.monitor.service.sqlserver;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.ts.TsMetricObjectDao;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import com.lzzh.monitor.service.instance.InstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlServerDiagnosticsServiceTest {
    private TsMetricObjectDao metricObjectDao;
    private InstanceService instanceService;
    private DataScopeService dataScopeService;
    private SqlServerDiagnosticsService service;

    @BeforeEach
    void setUp() {
        metricObjectDao = mock(TsMetricObjectDao.class);
        instanceService = mock(InstanceService.class);
        dataScopeService = mock(DataScopeService.class);
        service = new SqlServerDiagnosticsService(metricObjectDao, instanceService, dataScopeService);
    }

    @Test
    void aggregatesLatestObjectMetricsForVisibleSqlServerInstance() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(instanceService.getCollectTarget(7L)).thenReturn(target("SQLSERVER"));
        when(metricObjectDao.queryTopN(7L, "sqlserver.transaction.open_seconds", 200))
                .thenReturn(List.of(new TsMetricObjectDao.ObjectPoint("51", "session", 125D, 1_000L)));

        var result = service.overview(7L);

        assertThat(result.getInstanceId()).isEqualTo(7L);
        assertThat(result.getMetrics()).containsKey("sqlserver.transaction.open_seconds");
        assertThat(result.getMetrics().get("sqlserver.transaction.open_seconds"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getObjectName()).isEqualTo("51");
                    assertThat(item.getObjectType()).isEqualTo("session");
                    assertThat(item.getValue()).isEqualTo(125D);
                    assertThat(item.getCollectTimeMs()).isEqualTo(1_000L);
                });
    }

    @Test
    void rejectsInstanceOutsideCurrentDataScopeBeforeReadingInstance() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.of(Set.of(8L)));

        assertThatThrownBy(() -> service.overview(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权访问该实例");
        verify(instanceService, never()).getCollectTarget(7L);
        verify(metricObjectDao, never()).queryTopN(eq(7L), anyString(), eq(200));
    }

    @Test
    void rejectsNonSqlServerInstanceBeforeReadingMetrics() {
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        when(instanceService.getCollectTarget(7L)).thenReturn(target("MYSQL"));

        assertThatThrownBy(() -> service.overview(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该诊断功能仅支持 SQL Server 实例");
        verify(metricObjectDao, never()).queryTopN(eq(7L), anyString(), eq(200));
    }

    private static CollectTargetVo target(String dbType) {
        CollectTargetVo target = new CollectTargetVo();
        target.setId(7L);
        target.setDbType(dbType);
        return target;
    }
}
