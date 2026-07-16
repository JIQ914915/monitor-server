package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsSqlServerDiagnosticEventWriterMapper;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class TsSqlServerDiagnosticEventWriterImpl implements TsSqlServerDiagnosticEventWriter {
    private final TsSqlServerDiagnosticEventWriterMapper mapper;
    private final TsWriteBatchProperties properties;
    public TsSqlServerDiagnosticEventWriterImpl(TsSqlServerDiagnosticEventWriterMapper mapper,
                                                 TsWriteBatchProperties properties) {
        this.mapper=mapper; this.properties=properties;
    }
    @Override public void batchWrite(Long instanceId,List<Event> events) {
        if(events==null||events.isEmpty()) return;
        int size=Math.max(1,properties.getTopSqlChunkSize());
        for(int i=0;i<events.size();i+=size)
            mapper.insertBatch(instanceId,events.subList(i,Math.min(i+size,events.size())));
    }
}
