package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsCapacityObjectWriterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import java.util.List;

/** 对象级容量明细写入实现：批量 INSERT 到 metric_capacity_object（Hypertable）。 */
@Repository
public class TsCapacityObjectWriterImpl implements TsCapacityObjectWriter {

    private static final Logger log = LoggerFactory.getLogger(TsCapacityObjectWriterImpl.class);

    private final TsCapacityObjectWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsCapacityObjectWriterImpl(TsCapacityObjectWriterMapper mapper,
                                      TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(List<TsCapacityObjectPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        int chunkSize = Math.max(1, batchProperties.getCapacityObjectChunkSize());
        int chunks = 0;
        for (int i = 0; i < points.size(); i += chunkSize) {
            List<TsCapacityObjectPoint> chunk = points.subList(i, Math.min(i + chunkSize, points.size()));
            mapper.insertBatch(chunk);
            chunks++;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= batchProperties.getSlowLogMs()) {
            log.warn("capacity_object 写入偏慢 points={} chunks={} chunkSize={} costMs={}",
                    points.size(), chunks, chunkSize, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("capacity_object 写入完成 points={} chunks={} chunkSize={} costMs={}",
                    points.size(), chunks, chunkSize, elapsedMs);
        }
    }
}
