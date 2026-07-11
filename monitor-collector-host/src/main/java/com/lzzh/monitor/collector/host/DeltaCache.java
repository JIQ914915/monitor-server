package com.lzzh.monitor.collector.host;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主机 counter 指标的两次采样差值缓存（进程内）。
 *
 * <p>node_exporter 的 counter（node_cpu_seconds_total、node_disk_read_bytes_total 等）
 * 是自主机启动以来的累积值，需要两次采样求差换算为速率/占比：
 * 首个采样周期只建基线不产出，第二个周期起输出；counter 回卷（主机重启/exporter 重启导致
 * 当前值小于基线）时重建基线、本轮不产出，与 MySQL 侧 delta 指标口径一致。
 */
@Component
public class DeltaCache {

    private record Baseline(double value, long timestampMillis) {
    }

    /** hostId → (key → 基线)。 */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Baseline>> cache = new ConcurrentHashMap<>();

    /**
     * 求两次采样的差值速率（单位：每秒）。
     *
     * @param hostId 主机 ID
     * @param key    指标键（family + 维度聚合口径，由调用方保证稳定）
     * @param value  本次累积值
     * @param ts     本次采样时间（毫秒）
     * @return 每秒速率；无基线或 counter 回卷或间隔非法时返回 null（本轮不产出）
     */
    public Double rate(Long hostId, String key, double value, long ts) {
        Delta d = delta(hostId, key, value, ts);
        if (d == null || d.intervalMillis() <= 0) {
            return null;
        }
        return d.diff() / (d.intervalMillis() / 1000.0);
    }

    /**
     * 求两次采样的原始差值与间隔（供占比类计算：如 CPU 非空闲时间占比）。
     */
    public Delta delta(Long hostId, String key, double value, long ts) {
        ConcurrentHashMap<String, Baseline> hostCache =
                cache.computeIfAbsent(hostId, k -> new ConcurrentHashMap<>());
        Baseline prev = hostCache.put(key, new Baseline(value, ts));
        if (prev == null) {
            return null;
        }
        double diff = value - prev.value();
        if (diff < 0) {
            // counter 回卷：基线已更新为当前值，本轮不产出
            return null;
        }
        return new Delta(diff, ts - prev.timestampMillis());
    }

    /** 主机删除/长期不可达时清理基线，避免陈旧基线导致恢复后的首个差值异常。 */
    public void evict(Long hostId) {
        cache.remove(hostId);
    }

    public record Delta(double diff, long intervalMillis) {
    }
}
