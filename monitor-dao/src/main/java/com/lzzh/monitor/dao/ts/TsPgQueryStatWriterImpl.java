package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsPgQueryStatWriterMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TsPgQueryStatWriterImpl implements TsPgQueryStatWriter {
    private final TsPgQueryStatWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsPgQueryStatWriterImpl(TsPgQueryStatWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(Long instanceId, List<TsPgQueryStatPoint> points) {
        if (points == null || points.isEmpty()) return;
        int size = Math.max(1, batchProperties.getTopSqlChunkSize());
        for (int i = 0; i < points.size(); i += size) {
            mapper.insertBatch(instanceId, points.subList(i, Math.min(i + size, points.size())));
        }
    }
}