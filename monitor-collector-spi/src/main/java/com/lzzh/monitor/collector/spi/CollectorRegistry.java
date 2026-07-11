package com.lzzh.monitor.collector.spi;

import com.lzzh.monitor.common.enums.DbType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 采集器注册中心：启动时收集所有 DatabaseCollector 实现，按类型建索引。 */
@Component
public class CollectorRegistry {

    private final Map<DbType, DatabaseCollector> byType = new EnumMap<>(DbType.class);

    public CollectorRegistry(List<DatabaseCollector> collectors) {
        for (DatabaseCollector c : collectors) {
            byType.put(c.supportedType(), c);
        }
    }

    public DatabaseCollector find(DbType type) {
        return byType.get(type);
    }

    public DatabaseCollector find(String dbType) {
        DbType t = DbType.of(dbType);
        return t == null ? null : byType.get(t);
    }
}
