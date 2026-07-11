package com.lzzh.monitor.collector.spi;

import com.lzzh.monitor.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/** 工厂：按 dbType + version 解析采集器，找不到/不支持则抛业务异常（§14.1.4）。 */
@Component
public class CollectorFactory {

    private final CollectorRegistry registry;

    public CollectorFactory(CollectorRegistry registry) {
        this.registry = registry;
    }

    public DatabaseCollector getCollector(String dbType, String version) {
        DatabaseCollector c = registry.find(dbType);
        if (c == null) {
            throw new BusinessException("不支持的数据库类型: " + dbType);
        }
        if (!c.supportsVersion(version)) {
            throw new BusinessException(dbType + " 暂不支持版本: " + version);
        }
        return c;
    }
}
