package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsLongConnWriterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 长连接明细写入实现（metric_long_conn 超表，P3 Processlist）。 */
@Repository
public class TsLongConnWriterImpl implements TsLongConnWriter {

    private static final Logger log = LoggerFactory.getLogger(TsLongConnWriterImpl.class);

    /** info 列截断长度，防止超大 SQL 撑爆存储。 */
    private static final int MAX_INFO_LENGTH = 2000;

    private final TsLongConnWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsLongConnWriterImpl(TsLongConnWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(Long instanceId, List<TsLongConnPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsLongConnPoint> clipped = points.stream()
                .map(p -> new TsLongConnPoint(
                        p.connId(),
                        p.connUser(),
                        p.connHost(),
                        p.connDb(),
                        p.command(),
                        p.timeSeconds(),
                        p.state(),
                        p.info(),
                        p.timestampMillis()))
                .toList();
        long startNanos = System.nanoTime();
        int chunkSize = Math.max(1, batchProperties.getLongConnChunkSize());
        int chunks = 0;
        for (int i = 0; i < clipped.size(); i += chunkSize) {
            List<TsLongConnPoint> chunk = clipped.subList(i, Math.min(i + chunkSize, clipped.size()));
            mapper.insertBatch(instanceId, chunk);
            chunks++;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= batchProperties.getSlowLogMs()) {
            log.warn("long_conn 写入偏慢 instanceId={} points={} chunks={} chunkSize={} costMs={}",
                    instanceId, clipped.size(), chunks, chunkSize, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("long_conn 写入完成 instanceId={} points={} chunks={} chunkSize={} costMs={}",
                    instanceId, clipped.size(), chunks, chunkSize, elapsedMs);
        }
    }

    private static String clipInfo(String info) {
        if (info == null || info.length() <= MAX_INFO_LENGTH) {
            return info;
        }
        return info.substring(0, MAX_INFO_LENGTH);
    }
}
