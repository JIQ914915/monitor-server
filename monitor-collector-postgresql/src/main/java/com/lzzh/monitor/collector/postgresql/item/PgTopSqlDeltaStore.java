package com.lzzh.monitor.collector.postgresql.item;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PG Top SQL 累积快照差值计算器（对标 MySQL TopSqlDeltaStore）。
 *
 * <p>{@code pg_stat_statements} 各列均为自统计重置以来的累积值，本组件对每个
 * {@code (instanceId, datname, queryid)} 三元组维护上一快照，下一周期计算增量。
 * <ul>
 *   <li>首次采样：返回 null，调用方跳过不落库；</li>
 *   <li>计数器回绕（pg_stat_statements_reset / 实例重启 / 条目被逐出重建）：
 *       用本次快照重建基线，返回 null；</li>
 *   <li>条目 24h 未再出现时惰性清理，防止内存只增不减。</li>
 * </ul>
 */
@Component
public class PgTopSqlDeltaStore {

    /** key = instanceId:datname:queryid */
    private final Map<String, Snapshot> last = new ConcurrentHashMap<>();

    private static final long MAX_IDLE_MILLIS = 24L * 60 * 60 * 1000;
    private static final long PRUNE_INTERVAL_MILLIS = 60L * 60 * 1000;
    private final AtomicLong lastPruneMillis = new AtomicLong(System.currentTimeMillis());

    /**
     * 计算本周期增量。
     *
     * @param totalExecMs  累积总执行耗时（毫秒，pg_stat_statements.total_exec_time）
     * @param sharedRead   累积共享缓冲物理读块数
     * @param sharedHit    累积共享缓冲命中块数
     * @param tempWritten  累积临时块写入数
     * @return 增量；首次采样或回绕时返回 null
     */
    public Delta compute(long instanceId, String datname, String queryId,
                         long calls, double totalExecMs, long rows,
                         long sharedRead, long sharedHit, long tempWritten) {
        pruneStaleIfDue();
        String key = instanceId + ":" + (datname == null ? "" : datname) + ":" + queryId;
        long now = System.currentTimeMillis();
        Snapshot cur = new Snapshot(calls, totalExecMs, rows, sharedRead, sharedHit, tempWritten, now);
        Snapshot prev = last.put(key, cur);
        if (prev == null) {
            return null;
        }
        if (calls < prev.calls || totalExecMs < prev.totalExecMs || rows < prev.rows) {
            return null;
        }
        long dCalls = calls - prev.calls;
        if (dCalls == 0) {
            return null;
        }
        double dExecMs = totalExecMs - prev.totalExecMs;
        return new Delta(dCalls, dExecMs, rows - prev.rows,
                Math.max(0, sharedRead - prev.sharedRead),
                Math.max(0, sharedHit - prev.sharedHit),
                Math.max(0, tempWritten - prev.tempWritten));
    }

    /** 清除指定实例的全部缓存（实例删除/重置场景）。 */
    public void evict(long instanceId) {
        String prefix = instanceId + ":";
        last.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private void pruneStaleIfDue() {
        long now = System.currentTimeMillis();
        long lastPrune = lastPruneMillis.get();
        if (now - lastPrune < PRUNE_INTERVAL_MILLIS || !lastPruneMillis.compareAndSet(lastPrune, now)) {
            return;
        }
        long cutoff = now - MAX_IDLE_MILLIS;
        last.values().removeIf(s -> s.lastSeenMillis < cutoff);
    }

    private record Snapshot(long calls, double totalExecMs, long rows,
                            long sharedRead, long sharedHit, long tempWritten,
                            long lastSeenMillis) {
    }

    /**
     * 本周期增量。
     *
     * @param deltaCalls       执行次数增量
     * @param deltaExecMs      总执行耗时增量（毫秒）
     * @param deltaRows        返回/影响行数增量
     * @param deltaSharedRead  共享缓冲物理读块增量
     * @param deltaSharedHit   共享缓冲命中块增量
     * @param deltaTempWritten 临时块写入增量
     */
    public record Delta(long deltaCalls, double deltaExecMs, long deltaRows,
                        long deltaSharedRead, long deltaSharedHit, long deltaTempWritten) {
    }
}
