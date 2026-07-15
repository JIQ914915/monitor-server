package com.lzzh.monitor.collector.postgresql.version;

import com.lzzh.monitor.collector.spi.version.VersionAdapter;

/**
 * PostgreSQL 版本适配器：提供版本差异 SQL。
 * <p>一期覆盖的 pg_stat_activity / pg_stat_database / pg_locks / pg_stat_replication
 * 等系统视图在 13–16 间字段稳定，暂由 {@link Pg13Adapter} 单适配器通配（floor 回退）；
 * 后续版本出现字段差异时按 MySQL 模式增加 Pg1xAdapter 覆盖对应方法。
 */
public interface PgVersionAdapter extends VersionAdapter {

    /** 连接概况：按状态统计 pg_stat_activity 中的客户端后端连接。 */
    String connectionsSql();

    /** 当前库累计计数器（事务/元组/缓存/临时文件/死锁），来自 pg_stat_database。 */
    String databaseStatSql();

    /** 锁等待：未授予锁数量与被锁阻塞的会话数。 */
    String locksSql();

    /** 事务时长：最长运行事务秒数、活跃/空闲事务数。 */
    String transactionsSql();

    /** 复制角色与延迟（主库：下游从库数；从库：回放延迟秒数）。 */
    String replicationSql();

    /** 容量：当前库与全实例的字节大小（小时级）。 */
    String capacitySql();

    /** 检查点与后台写入统计，统一输出归一化列名。 */
    String checkpointSql();

    /** pg_stat_io 汇总 SQL；版本不支持时返回 null。 */
    default String statIoSql() {
        return null;
    }
}
