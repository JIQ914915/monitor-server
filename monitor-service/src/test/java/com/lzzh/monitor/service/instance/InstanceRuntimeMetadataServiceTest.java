package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstanceRuntimeMetadataServiceTest {

    private DbInstanceMapper instanceMapper;
    private DatabaseTypeMapper typeMapper;
    private DatabaseVersionMapper versionMapper;
    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private InstanceRuntimeMetadataService service;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void setUp() {
        instanceMapper = mock(DbInstanceMapper.class);
        typeMapper = mock(DatabaseTypeMapper.class);
        versionMapper = mock(DatabaseVersionMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn((HashOperations) hashOperations);

        service = new InstanceRuntimeMetadataService();
        ReflectionTestUtils.setField(service, "instanceMapper", instanceMapper);
        ReflectionTestUtils.setField(service, "databaseTypeMapper", typeMapper);
        ReflectionTestUtils.setField(service, "databaseVersionMapper", versionMapper);
        ReflectionTestUtils.setField(service, "redisTemplate", redisTemplate);
    }

    @Test
    void returnsRedisValueWithoutQueryingDatabase() {
        when(hashOperations.entries("monitor:instance:runtime:7")).thenReturn(Map.of(
                "dbTypeId", "1",
                "dbVersionId", "10",
                "dbTypeCode", "POSTGRESQL",
                "dbTypeLabel", "PostgreSQL",
                "dbVersion", "16",
                "driverClass", "org.postgresql.Driver",
                "urlTemplate", "jdbc:postgresql://{host}:{port}/{database}"));

        InstanceRuntimeMetadata metadata = service.getRequired(7L);

        assertThat(metadata.dbTypeCode()).isEqualTo("POSTGRESQL");
        assertThat(metadata.dbVersion()).isEqualTo("16");
        assertThat(metadata.driverClass()).isEqualTo("org.postgresql.Driver");
        assertThat(metadata.urlTemplate()).contains("jdbc:postgresql");
        verify(instanceMapper, never()).selectById(any());
    }

    @Test
    void fallsBackToDatabaseAndRepopulatesRedisOnCacheMiss() {
        when(hashOperations.entries("monitor:instance:runtime:7")).thenReturn(Map.of());
        DbInstance instance = new DbInstance();
        instance.setId(7L);
        instance.setDbTypeId(1L);
        instance.setDbVersionId(10L);
        DatabaseType type = new DatabaseType();
        type.setId(1L);
        type.setCode("postgresql");
        type.setLabel("PostgreSQL");
        type.setDriverClass("org.postgresql.Driver");
        type.setUrlTemplate("jdbc:postgresql://{host}:{port}/{database}");
        DatabaseVersion version = new DatabaseVersion();
        version.setId(10L);
        version.setVersionCode("16");
        when(instanceMapper.selectById(7L)).thenReturn(instance);
        when(typeMapper.selectById(1L)).thenReturn(type);
        when(versionMapper.selectById(10L)).thenReturn(version);

        InstanceRuntimeMetadata metadata = service.getRequired(7L);

        assertThat(metadata.dbTypeCode()).isEqualTo("POSTGRESQL");
        assertThat(metadata.dbVersion()).isEqualTo("16");
        assertThat(metadata.driverClass()).isEqualTo("org.postgresql.Driver");
        assertThat(metadata.urlTemplate()).contains("jdbc:postgresql");
        verify(hashOperations).putAll(anyString(), any());
        verify(redisTemplate).expire("monitor:instance:runtime:7", Duration.ofHours(24));
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        when(hashOperations.entries("monitor:instance:runtime:7"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        DbInstance instance = new DbInstance();
        instance.setId(7L);
        instance.setDbTypeId(1L);
        instance.setDbVersionId(10L);
        DatabaseType type = new DatabaseType();
        type.setId(1L);
        type.setCode("mysql");
        type.setLabel("MySQL");
        DatabaseVersion version = new DatabaseVersion();
        version.setId(10L);
        version.setVersionCode("8.0");
        when(instanceMapper.selectById(7L)).thenReturn(instance);
        when(typeMapper.selectById(1L)).thenReturn(type);
        when(versionMapper.selectById(10L)).thenReturn(version);

        InstanceRuntimeMetadata metadata = service.getRequired(7L);

        assertThat(metadata.dbTypeCode()).isEqualTo("MYSQL");
        assertThat(metadata.dbVersion()).isEqualTo("8.0");
        verify(hashOperations, never()).putAll(anyString(), any());
    }
}
