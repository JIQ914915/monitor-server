package com.lzzh.monitor.service.datatype;

import com.lzzh.monitor.api.request.DatabaseVersionRequest;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseVersionServiceImplTest {

    private DatabaseVersionMapper mapper;
    private DatabaseTypeMapper databaseTypeMapper;
    private DatabaseVersionServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(DatabaseVersionMapper.class);
        databaseTypeMapper = mock(DatabaseTypeMapper.class);
        service = new DatabaseVersionServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "databaseTypeMapper", databaseTypeMapper);
    }

    @Test
    void listUsesTypeIdAndReturnsStableCode() {
        DatabaseType type = databaseType();
        DatabaseVersion version = new DatabaseVersion();
        version.setId(10L);
        version.setDbTypeId(3L);
        version.setVersionCode("2022");
        version.setVersionName("SQL Server 2022");
        version.setSortOrder(1);
        when(databaseTypeMapper.selectList(isNull())).thenReturn(List.of(type));
        when(databaseTypeMapper.selectById(3L)).thenReturn(type);
        when(mapper.selectList(any())).thenReturn(List.of(version));

        assertThat(service.list(3L)).singleElement().satisfies(item -> {
            assertThat(item.getDbTypeId()).isEqualTo(3L);
            assertThat(item.getDbType()).isEqualTo("SQLSERVER");
            assertThat(item.getVersionCode()).isEqualTo("2022");
        });
    }

    @Test
    void createPersistsTypeIdInsteadOfCode() {
        when(databaseTypeMapper.selectById(3L)).thenReturn(databaseType());
        when(mapper.insert(any(DatabaseVersion.class))).thenAnswer(invocation -> {
            invocation.<DatabaseVersion>getArgument(0).setId(10L);
            return 1;
        });
        DatabaseVersionRequest request = new DatabaseVersionRequest();
        request.setDbTypeId(3L);
        request.setVersionCode("2022");

        assertThat(service.create(request)).isEqualTo(10L);

        ArgumentCaptor<DatabaseVersion> captor = ArgumentCaptor.forClass(DatabaseVersion.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getDbTypeId()).isEqualTo(3L);
        assertThat(captor.getValue().getVersionName()).isEqualTo("SQL Server 2022");
    }

    private static DatabaseType databaseType() {
        DatabaseType type = new DatabaseType();
        type.setId(3L);
        type.setCode("SQLSERVER");
        type.setLabel("SQL Server");
        return type;
    }
}
