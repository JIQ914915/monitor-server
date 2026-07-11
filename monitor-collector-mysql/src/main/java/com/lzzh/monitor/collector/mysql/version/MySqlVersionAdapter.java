package com.lzzh.monitor.collector.mysql.version;

import com.lzzh.monitor.collector.spi.version.VersionAdapter;

/**
 * MySQL 版本差异适配接口：把 5.6/5.7/8.0 在采集项上的差异（SQL、数据源对象）收敛到各实现。
 * 新增 MySQL 版本（如 8.4）= 新增一个本接口实现并在 Resolver 注册，互不影响（§5.8）。
 */
public interface MySqlVersionAdapter extends VersionAdapter {

    /** 锁等待查询 SQL（5.6 无 P_S、5.7 用 sys、8.0 用 data_lock_waits）。 */
    String lockWaitsSql();

    /**
     * 复制状态查询 SQL：5.6/5.7 用 {@code SHOW SLAVE STATUS}，
     * 8.0.22+ 改用 {@code SHOW REPLICA STATUS}（术语 source/replica，§8.2）。
     */
    String replicaStatusSql();

    /** 是否支持 performance_schema 相关能力。 */
    boolean supportsPerformanceSchema();

    /**
     * 是否支持 {@code performance_schema.error_log} 表（MySQL 8.0.22+ 新增）。
     * 该表以结构化形式存储服务端错误日志，可直接 SQL 查询最近 Error/Warning 条数。
     * 5.6/5.7 无此表，返回 false；默认实现返回 false，8.0 适配器覆盖。
     */
    default boolean hasErrorLogTable() {
        return false;
    }

    /**
     * 是否支持 MySQL Group Replication 状态采集
     * （{@code performance_schema.replication_group_members}，内置规则仅面向 8.0+ MGR 集群）。
     * 默认返回 false，8.0 适配器覆盖。
     */
    default boolean supportsGroupReplication() {
        return false;
    }
}
