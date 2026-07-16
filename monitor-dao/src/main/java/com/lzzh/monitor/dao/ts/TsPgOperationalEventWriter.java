package com.lzzh.monitor.dao.ts;

import java.util.List;

public interface TsPgOperationalEventWriter {
    void batchWrite(Long instanceId, List<TsPgOperationalEvent> events);

    record TsPgOperationalEvent(String source, String category, String eventType, String severity,
            String databaseName, String userName, String objectName, String queryId, String sqlState,
            String message, String fingerprint, String payloadJson, boolean sensitiveRedacted,
            long eventTimeMillis) {}
}