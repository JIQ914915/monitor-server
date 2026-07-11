package com.lzzh.monitor.admin.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 按钮/接口级权限校验注解：menu:action（§11.11.6）。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {

    /** 所需权限码，如 instance:add、data_retention:write。 */
    String value();
}
