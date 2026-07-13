package com.lzzh.monitor.service.instance;

import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DatabaseVersion;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import com.lzzh.monitor.dao.mapper.DatabaseVersionMapper;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实例数据库类型/版本缓存。Redis 仅用于加速，缓存未命中或不可用时始终回源数据库。
 */
@Service
public class InstanceRuntimeMetadataService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InstanceRuntimeMetadataService.class);
    private static final String KEY_PREFIX = "monitor:instance:runtime:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final long REDIS_RETRY_INTERVAL_MS = 30_000L;

    @Resource
    private DbInstanceMapper instanceMapper;
    @Resource
    private DatabaseTypeMapper databaseTypeMapper;
    @Resource
    private DatabaseVersionMapper databaseVersionMapper;
    @Resource
    private StringRedisTemplate redisTemplate;

    private final AtomicLong redisUnavailableUntil = new AtomicLong(0L);

    public InstanceRuntimeMetadata getRequired(Long instanceId) {
        if (instanceId == null) {
            throw new BusinessException("实例ID不能为空");
        }
        InstanceRuntimeMetadata cached = readCache(instanceId);
        if (cached != null) {
            return cached;
        }
        InstanceRuntimeMetadata loaded = loadFromDatabase(instanceId);
        writeCache(loaded);
        return loaded;
    }

    /** 实例新增或修改成功后，用数据库最新值覆盖缓存。 */
    public void refresh(Long instanceId) {
        if (instanceId == null) {
            return;
        }
        try {
            writeCache(loadFromDatabase(instanceId));
        } catch (RuntimeException e) {
            log.warn("刷新实例 {} 运行元数据缓存失败，后续请求将回源数据库: {}", instanceId, e.getMessage());
        }
    }

    /** 实例删除成功后同步删除缓存。 */
    public void evict(Long instanceId) {
        if (instanceId == null || !redisAvailable()) {
            return;
        }
        try {
            redisTemplate.delete(key(instanceId));
        } catch (RuntimeException e) {
            markRedisUnavailable("删除实例元数据缓存", e);
        }
    }

    /** admin/collector 启动完成后全量预热；失败不阻断服务启动。 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            warmUp();
        } catch (RuntimeException e) {
            log.warn("实例运行元数据预热失败，后续请求将回源数据库: {}", e.getMessage());
        }
    }

    public void warmUp() {
        List<DbInstance> instances = instanceMapper.selectList(null);
        Map<Long, DatabaseType> types = new HashMap<>();
        for (DatabaseType type : databaseTypeMapper.selectList(null)) {
            types.put(type.getId(), type);
        }
        Map<Long, DatabaseVersion> versions = new HashMap<>();
        for (DatabaseVersion version : databaseVersionMapper.selectList(null)) {
            versions.put(version.getId(), version);
        }
        List<InstanceRuntimeMetadata> metadataList = instances.stream()
                .map(instance -> toMetadata(instance,
                        types.get(instance.getDbTypeId()), versions.get(instance.getDbVersionId())))
                .filter(java.util.Objects::nonNull)
                .toList();
        boolean redisWritten = writeCaches(metadataList);
        log.info("实例运行元数据预热完成，共 {} 条，Redis写入={}", metadataList.size(), redisWritten);
    }

    private InstanceRuntimeMetadata loadFromDatabase(Long instanceId) {
        DbInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException("实例不存在: " + instanceId);
        }
        DatabaseType type = instance.getDbTypeId() == null
                ? null : databaseTypeMapper.selectById(instance.getDbTypeId());
        DatabaseVersion version = instance.getDbVersionId() == null
                ? null : databaseVersionMapper.selectById(instance.getDbVersionId());
        InstanceRuntimeMetadata metadata = toMetadata(instance, type, version);
        if (metadata == null) {
            throw new BusinessException("实例数据库类型或版本配置无效: " + instanceId);
        }
        return metadata;
    }

    private static InstanceRuntimeMetadata toMetadata(DbInstance instance,
                                                       DatabaseType type,
                                                       DatabaseVersion version) {
        if (instance == null || type == null || version == null
                || !StringUtils.hasText(type.getCode()) || !StringUtils.hasText(version.getVersionCode())) {
            return null;
        }
        return new InstanceRuntimeMetadata(instance.getId(), type.getId(), version.getId(),
                type.getCode().trim().toUpperCase(),
                StringUtils.hasText(type.getLabel()) ? type.getLabel() : type.getCode(),
                version.getVersionCode(), type.getDriverClass(), type.getUrlTemplate());
    }

    private InstanceRuntimeMetadata readCache(Long instanceId) {
        if (!redisAvailable()) {
            return null;
        }
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(key(instanceId));
            if (values.isEmpty()) {
                return null;
            }
            Long dbTypeId = parseLong(values.get("dbTypeId"));
            Long dbVersionId = parseLong(values.get("dbVersionId"));
            String dbTypeCode = stringValue(values.get("dbTypeCode"));
            String dbTypeLabel = stringValue(values.get("dbTypeLabel"));
            String dbVersion = stringValue(values.get("dbVersion"));
            String driverClass = stringValue(values.get("driverClass"));
            String urlTemplate = stringValue(values.get("urlTemplate"));
            if (dbTypeId == null || dbVersionId == null || !StringUtils.hasText(dbTypeCode)
                    || !StringUtils.hasText(dbVersion)) {
                redisTemplate.delete(key(instanceId));
                return null;
            }
            return new InstanceRuntimeMetadata(instanceId, dbTypeId, dbVersionId,
                    dbTypeCode, dbTypeLabel, dbVersion, driverClass, urlTemplate);
        } catch (RuntimeException e) {
            markRedisUnavailable("读取实例元数据缓存", e);
            return null;
        }
    }

    private void writeCache(InstanceRuntimeMetadata metadata) {
        if (metadata == null || !redisAvailable()) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        values.put("dbTypeId", String.valueOf(metadata.dbTypeId()));
        values.put("dbVersionId", String.valueOf(metadata.dbVersionId()));
        values.put("dbTypeCode", metadata.dbTypeCode());
        values.put("dbTypeLabel", metadata.dbTypeLabel());
        values.put("dbVersion", metadata.dbVersion());        putIfNotNull(values, "driverClass", metadata.driverClass());
        putIfNotNull(values, "urlTemplate", metadata.urlTemplate());
        try {
            redisTemplate.delete(key(metadata.instanceId()));
            redisTemplate.opsForHash().putAll(key(metadata.instanceId()), values);
            redisTemplate.expire(key(metadata.instanceId()), CACHE_TTL);
        } catch (RuntimeException e) {
            markRedisUnavailable("写入实例元数据缓存", e);
        }
    }

    private boolean writeCaches(List<InstanceRuntimeMetadata> metadataList) {
        if (metadataList.isEmpty() || !redisAvailable()) {
            return false;
        }
        RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
        try {
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (InstanceRuntimeMetadata metadata : metadataList) {
                    byte[] redisKey = serializer.serialize(key(metadata.instanceId()));
                    Map<byte[], byte[]> values = new HashMap<>();
                    values.put(serializer.serialize("dbTypeId"),
                            serializer.serialize(String.valueOf(metadata.dbTypeId())));
                    values.put(serializer.serialize("dbVersionId"),
                            serializer.serialize(String.valueOf(metadata.dbVersionId())));
                    values.put(serializer.serialize("dbTypeCode"), serializer.serialize(metadata.dbTypeCode()));
                    values.put(serializer.serialize("dbTypeLabel"), serializer.serialize(metadata.dbTypeLabel()));
                    values.put(serializer.serialize("dbVersion"), serializer.serialize(metadata.dbVersion()));
                    putSerializedIfNotNull(values, serializer, "driverClass", metadata.driverClass());
                    putSerializedIfNotNull(values, serializer, "urlTemplate", metadata.urlTemplate());
                    connection.keyCommands().del(redisKey);
                    connection.hashCommands().hMSet(redisKey, values);
                    connection.keyCommands().expire(redisKey, CACHE_TTL);
                }
                return null;
            });
            return true;
        } catch (RuntimeException e) {
            markRedisUnavailable("批量预热实例元数据缓存", e);
            return false;
        }
    }
    private static void putIfNotNull(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private static void putSerializedIfNotNull(Map<byte[], byte[]> values,
                                               RedisSerializer<String> serializer,
                                               String key,
                                               String value) {
        if (value != null) {
            values.put(serializer.serialize(key), serializer.serialize(value));
        }
    }
    private boolean redisAvailable() {
        return System.currentTimeMillis() >= redisUnavailableUntil.get();
    }

    private void markRedisUnavailable(String operation, RuntimeException e) {
        redisUnavailableUntil.set(System.currentTimeMillis() + REDIS_RETRY_INTERVAL_MS);
        log.warn("{}失败，30秒内降级为数据库查询: {}", operation, e.getMessage());
    }

    private static String key(Long instanceId) {
        return KEY_PREFIX + instanceId;
    }

    private static Long parseLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}