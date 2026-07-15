package com.lzzh.monitor.collector.postgresql.item;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** pg_stat_statements 累积快照差值计算，重置或回绕时只重建基线。 */
@Component
public class PgTopSqlDeltaStore {

    private final Map<String, Snapshot> last = new ConcurrentHashMap<>();
    private final Map<String, ExtendedSnapshot> extendedLast = new ConcurrentHashMap<>();
    private static final long MAX_IDLE_MILLIS = 24L * 60 * 60 * 1000;
    private static final long PRUNE_INTERVAL_MILLIS = 60L * 60 * 1000;
    private final AtomicLong lastPruneMillis = new AtomicLong(System.currentTimeMillis());

    /** 兼容既有 Top SQL 榜单的核心差值入口。 */
    public Delta compute(long instanceId, String datname, String queryId,
                         long calls, double totalExecMs, long rows,
                         long sharedRead, long sharedHit, long tempWritten) {
        pruneStaleIfDue();
        String key = key(instanceId, datname, queryId);
        long now = System.currentTimeMillis();
        Snapshot cur = new Snapshot(calls, totalExecMs, rows, sharedRead, sharedHit, tempWritten, now);
        Snapshot prev = last.put(key, cur);
        if (prev == null || calls < prev.calls || totalExecMs < prev.totalExecMs || rows < prev.rows) {
            return null;
        }
        long dCalls = calls - prev.calls;
        if (dCalls == 0) return null;
        return new Delta(dCalls, totalExecMs - prev.totalExecMs, rows - prev.rows,
                positive(sharedRead, prev.sharedRead), positive(sharedHit, prev.sharedHit),
                positive(tempWritten, prev.tempWritten));
    }

    /** Query Analytics 完整差值；stats_reset 变化时禁止把计数器回落识别成异常。 */
    public ExtendedDelta computeExtended(long instanceId, String datname, String queryId,
                                         long calls, double totalExecMs, long rows,
                                         Map<String, Double> counters, String statsReset) {
        pruneStaleIfDue();
        String key = key(instanceId, datname, queryId);
        long now = System.currentTimeMillis();
        ExtendedSnapshot cur = new ExtendedSnapshot(calls, totalExecMs, rows,
                new LinkedHashMap<>(counters), statsReset, now);
        ExtendedSnapshot prev = extendedLast.put(key, cur);
        if (prev == null || calls < prev.calls || totalExecMs < prev.totalExecMs
                || rows < prev.rows || !java.util.Objects.equals(statsReset, prev.statsReset)) {
            return null;
        }
        long dCalls = calls - prev.calls;
        if (dCalls == 0) return null;
        Map<String, Double> delta = new LinkedHashMap<>();
        counters.forEach((name, value) -> {
            double before = prev.counters.getOrDefault(name, 0d);
            delta.put(name, Math.max(0d, value - before));
        });
        return new ExtendedDelta(dCalls, totalExecMs - prev.totalExecMs,
                Math.max(0, rows - prev.rows), delta);
    }

    public void evict(long instanceId) {
        String prefix = instanceId + ":";
        last.keySet().removeIf(k -> k.startsWith(prefix));
        extendedLast.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private void pruneStaleIfDue() {
        long now = System.currentTimeMillis();
        long previous = lastPruneMillis.get();
        if (now - previous < PRUNE_INTERVAL_MILLIS
                || !lastPruneMillis.compareAndSet(previous, now)) return;
        long cutoff = now - MAX_IDLE_MILLIS;
        last.values().removeIf(s -> s.lastSeenMillis < cutoff);
        extendedLast.values().removeIf(s -> s.lastSeenMillis < cutoff);
    }

    private static String key(long instanceId, String database, String queryId) {
        return instanceId + ":" + (database == null ? "" : database) + ":" + queryId;
    }

    private static long positive(long current, long previous) {
        return Math.max(0, current - previous);
    }

    private record Snapshot(long calls, double totalExecMs, long rows,
                            long sharedRead, long sharedHit, long tempWritten,
                            long lastSeenMillis) {
    }

    private record ExtendedSnapshot(long calls, double totalExecMs, long rows,
                                    Map<String, Double> counters, String statsReset,
                                    long lastSeenMillis) {
    }

    public record Delta(long deltaCalls, double deltaExecMs, long deltaRows,
                        long deltaSharedRead, long deltaSharedHit, long deltaTempWritten) {
    }

    public record ExtendedDelta(long deltaCalls, double deltaExecMs, long deltaRows,
                                Map<String, Double> metrics) {
    }
}