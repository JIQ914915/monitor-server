package com.lzzh.monitor.dao.ts;

import java.util.List;

public interface PgCollectItemStatusWriter {
    void batchUpsert(Long instanceId, List<ItemStatus> statuses);
    record ItemStatus(String frequency, String itemCode, String status, String reason,
                      int durationMs, int rowCount, long collectedAtMillis) {}
}
