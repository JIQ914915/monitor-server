package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsTopSqlWriterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Top SQL 差值点写入实现（P1-3）。
 *
 * <p>仅写入 {@code hasDelta() == true} 的条目（首次采样 / 计数器回绕时跳过），
 * 保证 metric_top_sql 中 delta_* 列始终有意义。
 */
@Repository
public class TsTopSqlWriterImpl implements TsTopSqlWriter {

    private static final Logger log = LoggerFactory.getLogger(TsTopSqlWriterImpl.class);

    private final TsTopSqlWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsTopSqlWriterImpl(TsTopSqlWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(List<TsTopSqlPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        // 过滤掉首次采样/回绕的点（delta 全 null，不应落库）
        List<TsTopSqlPoint> effective = points.stream()
                .filter(TsTopSqlPoint::hasDelta)
                .toList();
        if (effective.isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        int chunkSize = Math.max(1, batchProperties.getTopSqlChunkSize());
        int chunks = 0;
        for (int i = 0; i < effective.size(); i += chunkSize) {
            List<TsTopSqlPoint> chunk = effective.subList(i, Math.min(i + chunkSize, effective.size()));
            mapper.insertBatch(chunk);
            chunks++;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= batchProperties.getSlowLogMs()) {
            log.warn("topsql 写入偏慢 points={} effective={} chunks={} chunkSize={} costMs={}",
                    points.size(), effective.size(), chunks, chunkSize, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("topsql 写入完成 points={} effective={} chunks={} chunkSize={} costMs={}",
                    points.size(), effective.size(), chunks, chunkSize, elapsedMs);
        }
    }
}
