package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.dao.mapper.TsMetricWriterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * TimescaleDB 数值时序写入实现：按频率路由到 metric_data_1m / 1h / 1d（Hypertable）。
 * <p>写入产品库（PG），数据源为应用主数据源；写入幂等由主键
 * (instance_id, metric_code, collect_time) + ON CONFLICT 覆盖保证（§21.2.2）。
 */
@Repository
public class TsMetricWriterImpl implements TsMetricWriter {

    private static final Logger log = LoggerFactory.getLogger(TsMetricWriterImpl.class);

    private final TsMetricWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsMetricWriterImpl(TsMetricWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(CollectFrequency frequency, List<TsMetricPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        int chunkSize = Math.max(1, batchProperties.getMetricChunkSize());
        int chunks = 0;
        CollectFrequency target = frequency == null ? CollectFrequency.MINUTE : frequency;
        for (int i = 0; i < points.size(); i += chunkSize) {
            List<TsMetricPoint> chunk = points.subList(i, Math.min(i + chunkSize, points.size()));
            switch (target) {
                case MINUTE -> mapper.upsertMetric1m(chunk);
                case HOURLY -> mapper.upsertMetric1h(chunk);
                case DAILY -> mapper.upsertMetric1d(chunk);
            }
            chunks++;
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= batchProperties.getSlowLogMs()) {
            log.warn("metric 写入偏慢 freq={} points={} chunks={} chunkSize={} costMs={}",
                    target, points.size(), chunks, chunkSize, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("metric 写入完成 freq={} points={} chunks={} chunkSize={} costMs={}",
                    target, points.size(), chunks, chunkSize, elapsedMs);
        }
    }
}
