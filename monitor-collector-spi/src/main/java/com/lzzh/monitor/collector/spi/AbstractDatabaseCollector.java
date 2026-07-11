package com.lzzh.monitor.collector.spi;

import com.lzzh.monitor.collector.spi.annotation.CollectorFor;
import com.lzzh.monitor.common.enums.DbType;

/** 采集器抽象基类：从 @CollectorFor 读取类型，复用通用逻辑。 */
public abstract class AbstractDatabaseCollector implements DatabaseCollector {

    @Override
    public DbType supportedType() {
        CollectorFor anno = getClass().getAnnotation(CollectorFor.class);
        if (anno == null) {
            throw new IllegalStateException(getClass().getName() + " 缺少 @CollectorFor 注解");
        }
        return anno.dbType();
    }
}
