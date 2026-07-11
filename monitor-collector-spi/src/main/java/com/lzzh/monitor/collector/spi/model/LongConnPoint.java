package com.lzzh.monitor.collector.spi.model;

/**
 * 长连接明细点（processlist TIME >= 阈值的单条连接快照）。
 * <p>落 {@code metric_long_conn} 专用表，保留 24 小时。
 *
 * @param connId        processlist id
 * @param connUser      连接账号
 * @param connHost      来源主机（host:port）
 * @param connDb        当前数据库
 * @param command       Command 列（Query/Execute 等，已过滤 Sleep）
 * @param timeSeconds   TIME 列（连接持续秒数）
 * @param state         State 列
 * @param info          当前 SQL（截断到 2000 字符）
 * @param timestampMillis 采集时间（毫秒）
 */
public record LongConnPoint(
        long connId,
        String connUser,
        String connHost,
        String connDb,
        String command,
        int timeSeconds,
        String state,
        String info,
        long timestampMillis
) {}
