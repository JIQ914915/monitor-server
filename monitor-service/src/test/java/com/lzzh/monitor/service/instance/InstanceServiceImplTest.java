package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.common.security.PasswordCipher;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.AlertEventMapper;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import com.lzzh.monitor.dao.mapper.HostMapper;
import com.lzzh.monitor.dao.mapper.InstanceDataCleanupMapper;
import com.lzzh.monitor.dao.mapper.InstanceGroupMapper;
import com.lzzh.monitor.dao.mapper.SysUserMapper;
import com.lzzh.monitor.service.datascope.DataScope;
import com.lzzh.monitor.service.datascope.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.Serializable;

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
    private DataScopeService dataScopeService;
    private InstanceDataCleanupMapper cleanupMapper;
    private InstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        dbInstanceMapper = mock(DbInstanceMapper.class);
        dataScopeService = mock(DataScopeService.class);
        cleanupMapper = mock(InstanceDataCleanupMapper.class);
        service = new InstanceServiceImpl(
                dbInstanceMapper,
                mock(DatabaseTypeMapper.class),
                mock(DatabaseVersionMapper.class),
                mock(PasswordCipher.class),
                dataScopeService,
                mock(AlertEventMapper.class),
                mock(InstanceGroupMapper.class),
                mock(SysUserMapper.class),
                mock(HostMapper.class),
                cleanupMapper);
        when(dataScopeService.currentScope()).thenReturn(DataScope.all());
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
}
