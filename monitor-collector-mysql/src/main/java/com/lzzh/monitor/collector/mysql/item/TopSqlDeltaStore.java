package com.lzzh.monitor.collector.mysql.item;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Top SQL 累积快照差值计算器（§9.3 Top SQL 加工）。
 *
 * <p>{@code performance_schema.events_statements_summary_by_digest} 各列均为自实例启动以来的累积值，
 * 不能直接用于"本周期 Top SQL 排名"。本组件对每个 {@code (instanceId, schemaName, digest)} 三元组
 * 维护上一快照，在下一采集周期时计算增量，供 {@link TopSqlItem} 落存差值。
 *
 * <ul>
 *   <li>首次采样（无上一快照）：各差值字段返回 null，调用方应跳过本条不写入（没有可比较的增量数据）；</li>
 *   <li>计数器回绕（实例重启 / P_S truncate，本次 count_star &lt; 上次）：视为无效，用本次重建基线，
 *       差值返回 null；</li>
 *   <li>同一实例下同一 digest 在不同周期的新增/消失均能正确处理（新 digest 首次 null，消失的不再触发）。</li>
 * </ul>
 *
 * <p>本组件为单例 Bean，跨调度周期保留内存快照；{@link CounterDeltaStore} 处理标量计数器，
 * 本类专门处理 Top SQL 多维对象。
 */
@Component
public class TopSqlDeltaStore {

    /** key = instanceId:schemaName:digest（schemaName 为空时用空串） */
    private final Map<String, Snapshot> last = new ConcurrentHashMap<>();

    /** 快照条目最长闲置时间：超过后视为该 digest 已不再出现，惰性清理防止内存只增不减。 */
    private static final long MAX_IDLE_MILLIS = 24L * 60 * 60 * 1000;
    /** 两次清理之间的最小间隔。 */
    private static final long PRUNE_INTERVAL_MILLIS = 60L * 60 * 1000;
    private final AtomicLong lastPruneMillis = new AtomicLong(System.currentTimeMillis());

    /**
     * 计算本周期相对上一快照的增量。
     *
     * @param instanceId    实例 ID
     * @param schemaName    库名（可为 null/空）
     * @param digest        SQL 指纹
     * @param countStar     本次累积执行次数
     * @param sumTimerWait  本次累积总耗时（皮秒）
     * @param rowsExamined  本次累积扫描行数
     * @param rowsSent      本次累积返回行数
     * @param lockTime      本次累积锁等待时间（皮秒，SUM_LOCK_TIME）
     * @param sortRows      本次累积排序行数（SUM_SORT_ROWS）
     * @param noIndexUsed   本次累积未使用索引的执行次数（SUM_NO_INDEX_USED）
     * @param tmpTables     本次累积创建内存临时表数（SUM_CREATED_TMP_TABLES）
     * @param tmpDiskTables 本次累积创建磁盘临时表数（SUM_CREATED_TMP_DISK_TABLES）
     * @return 差值结果；首次采样或检测到回绕时返回 null（调用方跳过写入）
     */
    public Delta compute(long instanceId, String schemaName, String digest,
                         long countStar, long sumTimerWait, long rowsExamined, long rowsSent,
                         long lockTime, long sortRows, long noIndexUsed,
                         long tmpTables, long tmpDiskTables) {
        pruneStaleIfDue();
        String key = instanceId + ":" + (schemaName == null ? "" : schemaName) + ":" + digest;
        long now = System.currentTimeMillis();
        Snapshot cur = new Snapshot(countStar, sumTimerWait, rowsExamined, rowsSent,
                lockTime, sortRows, noIndexUsed, tmpTables, tmpDiskTables, now);
        Snapshot prev = last.put(key, cur);

        if (prev == null) {
            return null;
        }
        // 计数器回绕（实例重启 / P_S truncate）：任一主累积列变小都视为基线失效，
        // 用本次快照重建基线并跳过本条，避免产生负增量
        if (countStar < prev.countStar || sumTimerWait < prev.sumTimerWait
                || rowsExamined < prev.rowsExamined || rowsSent < prev.rowsSent) {
            return null;
        }

        long dCount = countStar - prev.countStar;
        long dTimerWait = sumTimerWait - prev.sumTimerWait;
        long dRowsExamined = rowsExamined - prev.rowsExamined;
        long dRowsSent = rowsSent - prev.rowsSent;

        if (dCount == 0) {
            return null;
        }

        // 平均耗时（微秒）：皮秒 → 微秒 /1_000_000，再 / 执行次数
        long avgTimerWaitUs = dTimerWait / 1_000_000 / dCount;

        return new Delta(dCount, dTimerWait, avgTimerWaitUs, dRowsExamined, dRowsSent,
                nonNegative(lockTime - prev.lockTime),
                nonNegative(sortRows - prev.sortRows),
                nonNegative(noIndexUsed - prev.noIndexUsed),
                nonNegative(tmpTables - prev.tmpTables),
                nonNegative(tmpDiskTables - prev.tmpDiskTables));
    }

    /** 诊断类累积列各自截断为非负：局部回绕不影响主 delta 的有效性。 */
    private static long nonNegative(long v) {
        return Math.max(0, v);
    }

    /** 清除指定实例的全部缓存（实例删除/重置场景）。 */
    public void evict(long instanceId) {
        String prefix = instanceId + ":";
        last.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * 惰性清理长期未出现的 digest 条目：SQL 指纹集合会随业务变更持续累积，
     * 若不清理，600 实例长期运行后内存缓慢增长。每小时最多执行一次，
     * 清掉 24h 未被 compute 触达的条目（重新出现时按首次采样处理，仅损失一个周期增量）。
     */
    private void pruneStaleIfDue() {
        long now = System.currentTimeMillis();
        long lastPrune = lastPruneMillis.get();
        if (now - lastPrune < PRUNE_INTERVAL_MILLIS || !lastPruneMillis.compareAndSet(lastPrune, now)) {
            return;
        }
        long cutoff = now - MAX_IDLE_MILLIS;
        last.values().removeIf(s -> s.lastSeenMillis < cutoff);
    }

    // ── 数据结构 ─────────────────────────────────────────────────────────────

    private record Snapshot(long countStar, long sumTimerWait, long rowsExamined, long rowsSent,
                            long lockTime, long sortRows, long noIndexUsed,
                            long tmpTables, long tmpDiskTables,
                            long lastSeenMillis) {
    }

    /**
     * 本周期相对上一快照的增量。
     *
     * @param deltaCount         周期内执行次数增量
     * @param deltaTimerWait     周期内总耗时增量（皮秒）
     * @param avgTimerWaitUs     周期内平均单次耗时（微秒）= deltaTimerWait(皮秒) / 1e6 / deltaCount
     * @param deltaRowsExamined  周期内扫描行数增量
     * @param deltaRowsSent      周期内返回行数增量
     * @param deltaLockTime      周期内锁等待时间增量（皮秒）
     * @param deltaSortRows      周期内排序行数增量
     * @param deltaNoIndexUsed   周期内未使用索引的执行次数增量
     * @param deltaTmpTables     周期内创建内存临时表数增量
     * @param deltaTmpDiskTables 周期内创建磁盘临时表数增量
     */
    public record Delta(long deltaCount, long deltaTimerWait, long avgTimerWaitUs,
                        long deltaRowsExamined, long deltaRowsSent,
                        long deltaLockTime, long deltaSortRows, long deltaNoIndexUsed,
                        long deltaTmpTables, long deltaTmpDiskTables) {
    }
}
