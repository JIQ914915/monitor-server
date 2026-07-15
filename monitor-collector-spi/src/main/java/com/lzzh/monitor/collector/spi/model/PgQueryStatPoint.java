package com.lzzh.monitor.collector.spi.model;

import java.util.Map;

/** PostgreSQL Query Analytics 周期增量点。 */
public record PgQueryStatPoint(
        String databaseName,
        String userName,
        String queryId,
        String queryText,
        long deltaCalls,
        double deltaExecTimeMs,
        long deltaRows,
        Map<String, Double> metrics,
        String statsReset,
        long deallocations,
        long timestampMillis) {
}