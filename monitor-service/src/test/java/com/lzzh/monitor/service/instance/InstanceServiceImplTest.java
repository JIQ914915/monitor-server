package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.api.request.InstanceRequest;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.entity.SysDictItem;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.InstanceDataCleanupMapper;
import com.lzzh.monitor.dao.mapper.SysDictItemMapper;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InstanceServiceImplTest {

    private DbInstanceMapper dbInstanceMapper;
    private DatabaseTypeMapper databaseTypeMapper;
    private DatabaseVersionMapper databaseVersionMapper;
    private DataScopeService dataScopeService;
    private InstanceDataCleanupMapper cleanupMapper;
    private SysDictItemMapper sysDictItemMapper;
    private InstanceRuntimeMetadataService runtimeMetadataService;
    private InstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        dbInstanceMapper = mock(DbInstanceMapper.class);
        databaseTypeMapper = mock(DatabaseTypeMapper.class);
        databaseVersionMapper = mock(DatabaseVersionMapper.class);
        dataScopeService = mock(DataScopeService.class);
        cleanupMapper = mock(InstanceDataCleanupMapper.class);
        sysDictItemMapper = mock(SysDictItemMapper.class);
        runtimeMetadataService = mock(InstanceRuntimeMetadataService.class);
        service = new InstanceServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", dbInstanceMapper);
        ReflectionTestUtils.setField(service, "databaseTypeMapper", databaseTypeMapper);
        ReflectionTestUtils.setField(service, "databaseVersionMapper", databaseVersionMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "instanceDataCleanupMapper", cleanupMapper);
        ReflectionTestUtils.setField(service, "sysDictItemMapper", sysDictItemMapper);
        ReflectionTestUtils.setField(service, "runtimeMetadataService", runtimeMetadataService);
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
        SysDictItem objectScope = new SysDictItem();
        objectScope.setItemValue("all");
        when(sysDictItemMapper.selectList(any())).thenReturn(List.of(objectScope));
    }

    @Test
    void createRefreshesRuntimeMetadataCache() {
        mockValidSelection();
        when(dbInstanceMapper.insert(any(DbInstance.class))).thenAnswer(invocation -> {
            DbInstance instance = invocation.getArgument(0);
            instance.setId(7L);
            return 1;
        });
        InstanceRequest request = updateRequest(1L, 10L);

        assertThat(service.create(request)).isEqualTo(7L);

        verify(runtimeMetadataService).refresh(7L);
    }

    @Test
    void deleteCleansAssociatedDataBeforeDeletingInstance() {
        DbInstance instance = new DbInstance();
        instance.setId(7L);
        when(dbInstanceMapper.selectById(7L)).thenReturn(instance);

        service.delete(7L);

        InOrder order = inOrder(cleanupMapper, dbInstanceMapper);
        order.verify(cleanupMapper).deleteByInstanceId(7L);
        order.verify(dbInstanceMapper).deleteById(7L);
        verify(runtimeMetadataService).evict(7L);
    }

    @Test
    void deleteRejectsMissingInstanceWithoutCleaningData() {
        when(dbInstanceMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("实例不存在: 7");
        verifyNoInteractions(cleanupMapper);
        verify(dbInstanceMapper, never()).deleteById((Serializable) any());
    }

    @Test
    void updateAcceptsOmittedDatabaseTypeAndVersionAndPreservesStoredValues() {
        DbInstance existing = existingInstance();
        when(dbInstanceMapper.selectById(7L)).thenReturn(existing);
        mockValidSelection();
        InstanceRequest request = updateRequest(null, null);
        request.setHostId(99L);

        service.update(request);

        ArgumentCaptor<DbInstance> captor = ArgumentCaptor.forClass(DbInstance.class);
        verify(dbInstanceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDbTypeId()).isEqualTo(1L);
        assertThat(captor.getValue().getDbVersionId()).isEqualTo(10L);
        verify(runtimeMetadataService).refresh(7L);
    }

    @Test
    void updateRejectsDatabaseTypeChange() {
        when(dbInstanceMapper.selectById(7L)).thenReturn(existingInstance());

        InstanceRequest request = updateRequest(2L, 10L);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("实例创建后不允许修改数据库类型和版本");
        verify(dbInstanceMapper, never()).updateById(any(DbInstance.class));
    }

    @Test
    void updateRejectsDatabaseVersionChange() {
        when(dbInstanceMapper.selectById(7L)).thenReturn(existingInstance());

        InstanceRequest request = updateRequest(1L, 11L);

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("实例创建后不允许修改数据库类型和版本");
        verify(dbInstanceMapper, never()).updateById(any(DbInstance.class));
    }

    private void mockValidSelection() {
        DatabaseType type = new DatabaseType();
        type.setId(1L);
        type.setCode("MYSQL");
        DatabaseVersion version = new DatabaseVersion();
        version.setId(10L);
        version.setDbType("mysql");
        when(databaseTypeMapper.selectById(1L)).thenReturn(type);
        when(databaseVersionMapper.selectById(10L)).thenReturn(version);
    }

    private static DbInstance existingInstance() {
        DbInstance existing = new DbInstance();
        existing.setId(7L);
        existing.setDbTypeId(1L);
        existing.setDbVersionId(10L);
        return existing;
    }

    private static InstanceRequest updateRequest(Long dbTypeId, Long dbVersionId) {
        InstanceRequest request = new InstanceRequest();
        request.setId(7L);
        request.setDbTypeId(dbTypeId);
        request.setDbVersionId(dbVersionId);
        return request;
    }
}
