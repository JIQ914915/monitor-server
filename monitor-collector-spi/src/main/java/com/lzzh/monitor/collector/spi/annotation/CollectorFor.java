package com.lzzh.monitor.collector.spi.annotation;

import com.lzzh.monitor.common.enums.DbType;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注采集器实现支持的数据库类型与版本。
 * 被标注类即为 Spring 组件，启动时由 CollectorRegistry 自动注册——新增实现无需改工厂。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface CollectorFor {

    DbType dbType();

    /** 支持的版本；空数组表示该类型全版本通用。 */
    String[] versions() default {};
}
