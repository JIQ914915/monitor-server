package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.common.enums.CollectFrequency;
import com.lzzh.monitor.dao.mapper.TsTextWriterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文本/状态指标写入实现：覆盖变更存储（§9.1）+ 按频率分表路由（§21.2.5）。
 * <p>以内存缓存的"上次 value_hash"做变更检测：hash 未变化则跳过写入，仅在变化时插入
 * metric_text_data_1m/1h/1d，只保留变更历史点。应用重启后缓存为空，会为每个指标补写一次基线，可接受。
 */
@Repository
public class TsTextWriterImpl implements TsTextWriter {

    private static final Logger log = LoggerFactory.getLogger(TsTextWriterImpl.class);

    /** key = instanceId + ":" + metric -> 上次 value_hash。 */
    private final Map<String, String> lastHash = new ConcurrentHashMap<>();

    private final TsTextWriterMapper mapper;
    private final TsWriteBatchProperties batchProperties;

    public TsTextWriterImpl(TsTextWriterMapper mapper, TsWriteBatchProperties batchProperties) {
        this.mapper = mapper;
        this.batchProperties = batchProperties;
    }

    @Override
    public void batchWrite(CollectFrequency frequency, List<TsTextPoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        List<TsTextPoint> changed = new ArrayList<>();
        for (TsTextPoint p : points) {
            String key = p.instanceId() + ":" + p.metric();
            String prev = lastHash.get(key);
            if (prev != null && prev.equals(p.valueHash())) {
                continue;
            }
            changed.add(p);
        }
        if (changed.isEmpty()) {
            return;
        }
        long startNanos = System.nanoTime();
        int chunkSize = Math.max(1, batchProperties.getTextChunkSize());
        int chunks = 0;
        CollectFrequency target = frequency == null ? CollectFrequency.MINUTE : frequency;
        for (int i = 0; i < changed.size(); i += chunkSize) {
            List<TsTextPoint> chunk = changed.subList(i, Math.min(i + chunkSize, changed.size()));
            switch (target) {
                case MINUTE -> mapper.upsertText1m(chunk);
                case HOURLY -> mapper.upsertText1h(chunk);
                case DAILY -> mapper.upsertText1d(chunk);
            }
            chunks++;
            // 写库成功后再更新 hash 缓存：先更新会导致写库失败时该文本被误认为"已写入"，
            // 后续相同内容不再重试写入，覆盖变更历史永久缺失该点
            for (TsTextPoint p : chunk) {
                lastHash.put(p.instanceId() + ":" + p.metric(), p.valueHash());
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs >= batchProperties.getSlowLogMs()) {
            log.warn("text 写入偏慢 freq={} points={} changed={} chunks={} chunkSize={} costMs={}",
                    target, points.size(), changed.size(), chunks, chunkSize, elapsedMs);
        } else if (log.isDebugEnabled()) {
            log.debug("text 写入完成 freq={} points={} changed={} chunks={} chunkSize={} costMs={}",
                    target, points.size(), changed.size(), chunks, chunkSize, elapsedMs);
        }
    }
}
