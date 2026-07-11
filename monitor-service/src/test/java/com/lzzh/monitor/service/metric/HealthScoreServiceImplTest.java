package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.ts.TsMetricLatestDao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthScoreServiceImplTest {

    @Test
    void rejectsUnknownDatabaseTypeInsteadOfFallingBackToMySql() {
        TsMetricLatestDao latestDao = mock(TsMetricLatestDao.class);
        DbInstanceMapper instanceMapper = mock(DbInstanceMapper.class);
        DatabaseTypeMapper databaseTypeMapper = mock(DatabaseTypeMapper.class);
        HealthScoreServiceImpl service = new HealthScoreServiceImpl(latestDao, instanceMapper, databaseTypeMapper);

        DbInstance instance = new DbInstance();
        instance.setId(1L);
        instance.setDbTypeId(10L);
        DatabaseType databaseType = new DatabaseType();
        databaseType.setId(10L);
        databaseType.setCode("ORACLE");
        when(instanceMapper.selectById(1L)).thenReturn(instance);
        when(databaseTypeMapper.selectById(10L)).thenReturn(databaseType);

        assertThatThrownBy(() -> service.calculate(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ORACLE");
    }
}
