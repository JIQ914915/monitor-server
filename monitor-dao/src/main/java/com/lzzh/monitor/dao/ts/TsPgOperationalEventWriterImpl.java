package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsPgOperationalEventWriterMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class TsPgOperationalEventWriterImpl implements TsPgOperationalEventWriter {
    private final TsPgOperationalEventWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;
    public TsPgOperationalEventWriterImpl(TsPgOperationalEventWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper=mapper; this.batchProperties=batchProperties;
    }
    @Override public void batchWrite(Long instanceId, List<TsPgOperationalEvent> events) {
        if (events==null || events.isEmpty()) return;
        int size=Math.max(1,batchProperties.getTopSqlChunkSize());
        for(int i=0;i<events.size();i+=size) mapper.insertBatch(instanceId,events.subList(i,Math.min(i+size,events.size())));
    }
}