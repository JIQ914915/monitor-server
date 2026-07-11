package com.lzzh.monitor.collector.spi.model;

/**
 * 慢 SQL 真实执行样本点（events_statements_history 中耗时 >= long_query_time 的语句）。
 * <p>落 {@code metric_slow_sql_sample} 超表，保留天数由 {@code retention_config.slow_sql_sample} 统一管理（默认 7 天），分钟级采集。
 * history 每线程仅保留最近 10 条语句，样本为抽样而非全量。
 *
 * @param threadId        P_S THREAD_ID（与 eventId 组成去重键）
 * @param eventId         P_S EVENT_ID（线程内单调递增）
 * @param connUser        执行账号
 * @param connHost        来源主机
 * @param schemaName      当前库
 * @param digest          归一化指纹（关联 metric_top_sql）
 * @param sqlText         真实执行 SQL（含参数，截断到 4000 字符）
 * @param execTimeUs      执行耗时（微秒）
 * @param lockTimeUs      锁等待（微秒）
 * @param rowsExamined    扫描行数
 * @param rowsSent        返回行数
 * @param sortRows        排序行数
 * @param noIndexUsed     本次执行是否未使用索引
 * @param tmpTables       创建临时表数（内存+磁盘）
 * @param tmpDiskTables   创建磁盘临时表数
 * @param timestampMillis 采集时间（毫秒，近似执行结束时间）
 */
public record SlowSqlSamplePoint(
        long threadId,
        long eventId,
        String connUser,
        String connHost,
        String schemaName,
        String digest,
        String sqlText,
        long execTimeUs,
        long lockTimeUs,
        long rowsExamined,
        long rowsSent,
        long sortRows,
        boolean noIndexUsed,
        long tmpTables,
        long tmpDiskTables,
        long timestampMillis
) {}
