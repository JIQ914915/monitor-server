package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsSlowSqlSampleWriterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 慢 SQL 真实执行样本写入实现（metric_slow_sql_sample 超表）。 */
@Repository
public class TsSlowSqlSampleWriterImpl implements TsSlowSqlSampleWriter {

    private static final Logger log = LoggerFactory.getLogger(TsSlowSqlSampleWriterImpl.class);

    private final TsSlowSqlSampleWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsSlowSqlSampleWriterImpl(TsSlowSqlSampleWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(Long instanceId, List<TsSlowSqlSamplePoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        // 样本单行较大（真实 SQL），复用 topSql 批次大小
        int chunkSize = Math.max(1, batchProperties.getTopSqlChunkSize());
        int chunks = 0;
        for (int i = 0; i < points.size(); i += chunkSize) {
            List<TsSlowSqlSamplePoint> chunk = points.subList(i, Math.min(i + chunkSize, points.size()));
            mapper.insertBatch(instanceId, chunk);
            chunks++;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= batchProperties.getSlowLogMs()) {
            log.warn("slow_sql_sample 写入偏慢 instanceId={} points={} chunks={} costMs={}",
                    instanceId, points.size(), chunks, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("slow_sql_sample 写入完成 instanceId={} points={} chunks={} costMs={}",
                    instanceId, points.size(), chunks, elapsedMs);
        }
    }
}
