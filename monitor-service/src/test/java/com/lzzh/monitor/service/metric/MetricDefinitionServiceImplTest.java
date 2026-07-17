package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.MetricDefinitionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricDefinitionServiceImplTest {

    @Test
    void resolvesMetricDefinitionsByDatabaseTypeIdAndStableCode() {
        MetricDefinitionMapper metricMapper = mock(MetricDefinitionMapper.class);
        DatabaseTypeMapper typeMapper = mock(DatabaseTypeMapper.class);
        MetricDefinitionServiceImpl service = new MetricDefinitionServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", metricMapper);
        ReflectionTestUtils.setField(service, "databaseTypeMapper", typeMapper);

        MetricDefinition definition = new MetricDefinition();
        definition.setMetricCode("sqlserver.availability");
        definition.setDbType("SQLSERVER");
        when(metricMapper.selectList(any())).thenReturn(List.of(definition));

        DatabaseType type = new DatabaseType();
        type.setId(3L);
        type.setCode("SQLSERVER");
        when(typeMapper.selectById(3L)).thenReturn(type);

        service.refreshCache();

        assertThat(service.listByDbTypeId(3L))
                .extracting(MetricDefinition::getMetricCode)
                .containsExactly("sqlserver.availability");
    }
}
