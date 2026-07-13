package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadata;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthScoreServiceImplTest {

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
