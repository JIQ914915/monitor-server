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
        for(int i=0;i<events.size();i+=size) {
            List<TsPgOperationalEvent> chunk=events.subList(i,Math.min(i+size,events.size()));
            // 先比较旧快照追加状态变化事件，再覆盖当前快照。
            mapper.insertStateChanges(instanceId,chunk);
            mapper.upsertSnapshots(instanceId,chunk);
        }
    }
}