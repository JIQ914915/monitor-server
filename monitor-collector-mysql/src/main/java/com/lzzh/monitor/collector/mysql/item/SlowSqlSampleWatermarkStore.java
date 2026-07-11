package com.lzzh.monitor.collector.mysql.item;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 慢 SQL 样本去重水位存储（节点内内存态）。
 *
 * <p>events_statements_history 每线程保留最近 10 条语句，相邻两轮采集会重复看到同一批事件。
 * 按 (instanceId, threadId) 记录已采集的最大 EVENT_ID（线程内单调递增），
 * 低于等于水位的事件视为已采集过，跳过。
 *
 * <p>MySQL 重启后 THREAD_ID / EVENT_ID 重新分配，可能出现新事件 EVENT_ID 低于旧水位而被误跳过；
 * 该场景发生概率低且影响仅为漏采几条样本（下轮新事件即恢复），不做持久化与重启检测。
 * 采集节点自身重启后水位清空，首轮可能重复采集 history 中残留的旧语句，
 * 由查询侧按 (thread_id, event_id) 去重兜底。
 */
@Component
public class SlowSqlSampleWatermarkStore {

    /** instanceId -> (threadId -> 已采集的最大 eventId)。 */
    private final Map<Long, Map<Long, Long>> watermarks = new ConcurrentHashMap<>();

    /** 上限保护：单实例跟踪线程数超过阈值时整体重置（防止连接快速轮换导致缓存膨胀）。 */
    private static final int MAX_THREADS_PER_INSTANCE = 10_000;

    /** 该事件是否为新事件（高于水位）；是则推进水位并返回 true。 */
    public boolean advanceIfNew(long instanceId, long threadId, long eventId) {
        Map<Long, Long> byThread = watermarks.computeIfAbsent(instanceId, k -> new ConcurrentHashMap<>());
        if (byThread.size() > MAX_THREADS_PER_INSTANCE) {
            byThread.clear();
        }
        Long seen = byThread.get(threadId);
        if (seen != null && eventId <= seen) {
            return false;
        }
        byThread.merge(threadId, eventId, Math::max);
        return true;
    }

    /** 实例删除/暂停时清理。 */
    public void evict(long instanceId) {
        watermarks.remove(instanceId);
    }
}
