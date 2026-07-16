package com.lzzh.monitor.collector.spi.model;

/** SQL Server 诊断事件；payload 限长，fingerprint 用于幂等去重。 */
public record SqlServerDiagnosticEventPoint(
        String eventType,
        String databaseName,
        String severity,
        String fingerprint,
        String payload,
        boolean sensitiveRedacted,
        long eventTimeMillis) {}
