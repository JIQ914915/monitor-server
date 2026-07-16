package com.lzzh.monitor.collector.spi.model;

import java.util.Map;

/** PostgreSQL 日志、复制、HA、备份和运维任务的标准化只读事件。 */
public record PgOperationalEventPoint(
        String source, String category, String eventType, String severity,
        String databaseName, String userName, String objectName,
        String queryId, String sqlState, String message, String fingerprint,
        Map<String, Object> payload, boolean sensitiveRedacted,
        long eventTimeMillis) {
}