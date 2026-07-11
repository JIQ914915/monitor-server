package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
class DatabaseMetricCatalogRegistry {
    private final Map<DbType, DatabaseMetricCatalog> catalogs = new EnumMap<>(DbType.class);

    @Resource
    private List<DatabaseMetricCatalog> catalogList;

    @PostConstruct
    void init() {
        for (DatabaseMetricCatalog catalog : catalogList) {
            DatabaseMetricCatalog old = catalogs.put(catalog.supportedType(), catalog);
            if (old != null) throw new IllegalStateException("重复的指标目录: " + catalog.supportedType());
        }
    }

    DatabaseMetricCatalog get(DbType type) {
        DatabaseMetricCatalog catalog = catalogs.get(type);
        if (catalog == null) throw new BusinessException("该数据库类型暂不支持指标目录: " + type);
        return catalog;
    }
}
