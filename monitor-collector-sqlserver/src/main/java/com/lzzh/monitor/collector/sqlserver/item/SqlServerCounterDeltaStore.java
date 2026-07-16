package com.lzzh.monitor.collector.sqlserver.item;

import org.springframework.stereotype.Component;

import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/** 累计计数器差值存储：识别首采、重启回绕和非正时间间隔。 */
@Component
public class SqlServerCounterDeltaStore {
    private record Snapshot(double value, long timestampMillis) {}
    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    public OptionalDouble rate(Long instanceId, String metric, double value, long timestampMillis) {
        String key = instanceId + "|" + metric;
        Snapshot previous = snapshots.put(key, new Snapshot(value, timestampMillis));
        if (previous == null || timestampMillis <= previous.timestampMillis || value < previous.value) {
            return OptionalDouble.empty();
        }
        double seconds = (timestampMillis - previous.timestampMillis) / 1000.0;
        return seconds <= 0 ? OptionalDouble.empty()
                : OptionalDouble.of((value - previous.value) / seconds);
    }

    public void clear(Long instanceId) {
        String prefix = instanceId + "|";
        snapshots.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
