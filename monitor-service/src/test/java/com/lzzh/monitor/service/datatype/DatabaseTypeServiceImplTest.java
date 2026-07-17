package com.lzzh.monitor.service.datatype;

import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseTypeServiceImplTest {

    @Test
    void listTypeOptionsMatchesSqlServerVersionsByTypeId() {
        DatabaseTypeMapper typeMapper = mock(DatabaseTypeMapper.class);
        DatabaseVersionMapper versionMapper = mock(DatabaseVersionMapper.class);
        DatabaseTypeServiceImpl service = new DatabaseTypeServiceImpl();
        ReflectionTestUtils.setField(service, "typeMapper", typeMapper);
        ReflectionTestUtils.setField(service, "versionMapper", versionMapper);

        DatabaseType type = new DatabaseType();
        type.setId(3L);
        type.setCode("SQLSERVER");
        type.setLabel("SQL Server");
        type.setEnabled(true);

        DatabaseVersion version = new DatabaseVersion();
        version.setId(10L);
        version.setDbTypeId(3L);
        version.setVersionCode("2022");
        version.setVersionName("SQL Server 2022");
        version.setSortOrder(1);

        when(typeMapper.selectList(any())).thenReturn(List.of(type));
        when(versionMapper.selectList(any())).thenReturn(List.of(version));

        assertThat(service.listTypeOptions()).singleElement().satisfies(option -> {
            assertThat(option.getCode()).isEqualTo("SQLSERVER");
            assertThat(option.getVersions()).singleElement().satisfies(item ->
                    assertThat(item.getValue()).isEqualTo("2022"));
        });
    }
}
