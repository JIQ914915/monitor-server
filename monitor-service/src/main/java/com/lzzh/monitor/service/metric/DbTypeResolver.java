package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DatabaseType;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DatabaseTypeMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 将实例关联的 database_type 统一解析为受支持的领域类型，不允许静默回退。 */
@Component
class DbTypeResolver {
    private final DatabaseTypeMapper databaseTypeMapper;
    private final Map<Long, DbType> cache = new ConcurrentHashMap<>();

    DbTypeResolver(DatabaseTypeMapper databaseTypeMapper) {
        this.databaseTypeMapper = databaseTypeMapper;
    }

    DbType resolve(DbInstance instance) {
        if (instance.getDbTypeId() == null) {
            throw new BusinessException("实例未配置数据库类型");
        }
        return cache.computeIfAbsent(instance.getDbTypeId(), id -> {
            DatabaseType databaseType = databaseTypeMapper.selectById(id);
            String code = databaseType == null ? null : databaseType.getCode();
            DbType type = DbType.of(code);
            if (type == null) {
                throw new BusinessException("不支持的数据库类型: " + code);
            }
            return type;
        });
    }
}