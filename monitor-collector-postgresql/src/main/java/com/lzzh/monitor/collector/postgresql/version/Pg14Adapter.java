package com.lzzh.monitor.collector.postgresql.version;

/** PostgreSQL 14 适配器。显式注册支持版本，避免使用低版本适配器时产生错误降级提示。 */
public class Pg14Adapter extends Pg13Adapter {
    @Override public String version() { return "14"; }
}
