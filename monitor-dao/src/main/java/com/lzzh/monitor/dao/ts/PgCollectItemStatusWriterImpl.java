package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.PgCollectItemStatusWriterMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class PgCollectItemStatusWriterImpl implements PgCollectItemStatusWriter {
    private final PgCollectItemStatusWriterMapper mapper;
    public PgCollectItemStatusWriterImpl(PgCollectItemStatusWriterMapper mapper) { this.mapper = mapper; }
    @Override public void batchUpsert(Long instanceId, List<ItemStatus> statuses) {
        if (statuses != null && !statuses.isEmpty()) mapper.upsertBatch(instanceId, statuses);
    }
}
