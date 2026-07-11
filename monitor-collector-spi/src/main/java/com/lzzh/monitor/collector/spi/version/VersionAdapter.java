package com.lzzh.monitor.collector.spi.version;

/** 版本适配器标记接口：各数据库模块定义自己的版本差异方法子接口。 */
public interface VersionAdapter {

    /** 适配的版本号，如 5.7、8.0。 */
    String version();
}
