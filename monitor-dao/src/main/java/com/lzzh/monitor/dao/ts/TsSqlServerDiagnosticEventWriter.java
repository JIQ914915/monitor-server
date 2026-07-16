package com.lzzh.monitor.dao.ts;

import java.util.List;

public interface TsSqlServerDiagnosticEventWriter {
    void batchWrite(Long instanceId, List<Event> events);
    record Event(String eventType,String databaseName,String severity,String fingerprint,
                 String payload,boolean sensitiveRedacted,long eventTimeMillis) {}
}
