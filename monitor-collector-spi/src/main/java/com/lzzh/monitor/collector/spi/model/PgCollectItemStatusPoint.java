package com.lzzh.monitor.collector.spi.model;

/** PostgreSQL 单个采集项的当前质量状态；落库时按实例、频率和采集项覆盖更新。 */
public record PgCollectItemStatusPoint(
        String frequency,
        String itemCode,
        String status,
        String reason,
        int durationMs,
        int rowCount,
        long collectedAtMillis) {}
