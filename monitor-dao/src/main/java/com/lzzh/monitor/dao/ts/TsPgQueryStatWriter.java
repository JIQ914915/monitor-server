package com.lzzh.monitor.dao.ts;

import java.util.List;

/** PostgreSQL Query Analytics 历史写入。 */
public interface TsPgQueryStatWriter {
    void batchWrite(Long instanceId, List<TsPgQueryStatPoint> points);

    record TsPgQueryStatPoint(
            String databaseName,
            String userName,
            String queryId,
            String queryText,
            long deltaCalls,
            double deltaExecTimeMs,
            long deltaRows,
            String metricsJson,
            String statsReset,
            long deallocations,
            long timestampMillis) {
    }
}