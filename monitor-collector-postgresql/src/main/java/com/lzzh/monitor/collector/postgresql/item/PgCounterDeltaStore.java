package com.lzzh.monitor.collector.postgresql.item;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PG 累积计数器差值/速率计算器（纯内存）。
 *
 * <p>pg_stat_database 的 xact_commit / blks_hit 等计数器是"自 stats reset 以来的累积值"，
 * 必须与上一周期快照相减再除以实际时间差得到增量/速率。
 *
 * <p><b>基线策略</b>：与 MySQL CounterDeltaStore 一致，基线只保存在节点内存。
 * Collector 重启后首个采集周期无基线，速率/增量指标返回 null（跳过该周期），下一周期自动恢复。
 *
 * <p><b>计数器语义</b>：
 * <ul>
 *   <li>首次采样无上一快照：返回 null</li>
 *   <li>计数器回绕（stats reset / 实例重启，本次值 &lt; 上次值）：返回 null，以本次值重建基线</li>
 *   <li>速率按实际采集时间差计算，不假设固定 60 秒</li>
 * </ul>
 */
@Component
public class PgCounterDeltaStore {

    /** key = "instanceId:counterName" → 上次采样快照。 */
    private final Map<String, Sample> last = new ConcurrentHashMap<>();

    /**
     * 计算每秒速率。
     *
     * @return 每秒速率；首次采样或计数器回绕时返回 null。
     */
    public Double rate(long instanceId, String name, long value, long tsMillis) {
        Sample prev = last.put(instanceId + ":" + name, new Sample(tsMillis, value));
        if (prev == null) return null;
        double intervalSec = (tsMillis - prev.ts) / 1000.0;
        long delta = value - prev.value;
        if (intervalSec <= 0 || delta < 0) return null;
        return delta / intervalSec;
    }

    /**
     * 计算周期增量（本次 - 上次）。
     *
     * @return 周期增量；首次采样或计数器回绕时返回 null。
     */
    public Long delta(long instanceId, String name, long value, long tsMillis) {
        Sample prev = last.put(instanceId + ":" + name, new Sample(tsMillis, value));
        if (prev == null) return null;
        long delta = value - prev.value;
        return delta < 0 ? null : delta;
    }

    private record Sample(long ts, long value) {}
}
