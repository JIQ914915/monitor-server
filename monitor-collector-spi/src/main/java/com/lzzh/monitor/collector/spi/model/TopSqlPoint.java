package com.lzzh.monitor.collector.spi.model;

/**
 * Top SQL / 语句摘要（digest）明细点（§9.3 / §21.2.5）。
 *
 * <p>源自 performance_schema.events_statements_summary_by_digest 的累积快照，经采集侧
 * {@link com.lzzh.monitor.collector.mysql.item.TopSqlDeltaStore} 差值计算后，
 * 以"本周期增量"形式写入 metric_top_sql 专用表（P1-3 架构决策）。
 *
 * <p>采集侧完成差值，省去独立的加工任务，前端可直接使用 delta_count / avg_timer_wait_us 做本周期 Top SQL 排名。
 * 首次采集无上一快照时，调用方应跳过本条不落库（delta 字段均为 null）。
 *
 * @param schemaName        库名（可空）
 * @param digest            SQL 指纹
 * @param digestText        归一化 SQL 文本（截断至 2000 字符）
 * @param countStar         本次累积执行次数（原始快照，保留用于回绕检测）
 * @param sumTimerWait      本次累积总耗时（皮秒，原始快照）
 * @param rowsExamined      本次累积扫描行数（原始快照）
 * @param rowsSent          本次累积返回行数（原始快照）
 * @param deltaCount        周期内执行次数增量（null = 首次采样 / 回绕）
 * @param deltaTimerWait    周期内总耗时增量（皮秒，null = 首次 / 回绕）
 * @param avgTimerWaitUs    周期内平均单次耗时（微秒，= deltaTimerWait(皮秒)/1e6/deltaCount）
 * @param deltaRowsExamined 周期内扫描行数增量
 * @param deltaRowsSent     周期内返回行数增量
 * @param deltaLockTime     周期内锁等待时间增量（皮秒）
 * @param deltaSortRows     周期内排序行数增量
 * @param deltaNoIndexUsed  周期内未使用索引的执行次数增量
 * @param deltaTmpTables    周期内创建内存临时表数增量
 * @param deltaTmpDiskTables 周期内创建磁盘临时表数增量
 * @param timestampMillis   采集时间（毫秒）
 */
public record TopSqlPoint(String schemaName, String digest, String digestText,
                          long countStar, long sumTimerWait, long rowsExamined, long rowsSent,
                          Long deltaCount, Long deltaTimerWait, Long avgTimerWaitUs,
                          Long deltaRowsExamined, Long deltaRowsSent,
                          Long deltaLockTime, Long deltaSortRows, Long deltaNoIndexUsed,
                          Long deltaTmpTables, Long deltaTmpDiskTables,
                          Long deltaPhysicalReads, Long deltaWrites,
                          long timestampMillis) {

    /** 兼容 MySQL/PG 现有完整构造。 */
    public TopSqlPoint(String schemaName, String digest, String digestText,
                       long countStar, long sumTimerWait, long rowsExamined, long rowsSent,
                       Long deltaCount, Long deltaTimerWait, Long avgTimerWaitUs,
                       Long deltaRowsExamined, Long deltaRowsSent,
                       Long deltaLockTime, Long deltaSortRows, Long deltaNoIndexUsed,
                       Long deltaTmpTables, Long deltaTmpDiskTables, long timestampMillis) {
        this(schemaName,digest,digestText,countStar,sumTimerWait,rowsExamined,rowsSent,
                deltaCount,deltaTimerWait,avgTimerWaitUs,deltaRowsExamined,deltaRowsSent,
                deltaLockTime,deltaSortRows,deltaNoIndexUsed,deltaTmpTables,deltaTmpDiskTables,
                null,null,timestampMillis);
    }

    /** 兼容旧构造（纯累积快照，delta 全部 null）。 */
    public TopSqlPoint(String schemaName, String digest, String digestText,
                       long countStar, long sumTimerWait, long rowsExamined, long rowsSent,
                       long timestampMillis) {
        this(schemaName, digest, digestText, countStar, sumTimerWait, rowsExamined, rowsSent,
                null, null, null, null, null, null, null, null, null, null, null, null, timestampMillis);
    }

    /** 是否拥有有效差值数据（false = 首次采样或计数器回绕，不应落库）。 */
    public boolean hasDelta() {
        return deltaCount != null;
    }
}
