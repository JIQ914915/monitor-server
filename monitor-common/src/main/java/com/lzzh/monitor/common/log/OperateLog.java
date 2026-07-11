package com.lzzh.monitor.common.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 操作日志注解：标注于写操作方法，由 AOP 落审计（§11.11.2 / §12.2）。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperateLog {

    /** 操作模块，如「实例管理」「数据保留」。 */
    String module() default "";

    /** 操作动作，如「新增」「修改」「删除」。 */
    String action() default "";
}
