package com.lzzh.monitor.collector.mysql.item;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在途慢查询完整 SQL 捕获缓存（节点内内存态）。
 *
 * <p>performance_schema 的 SQL_TEXT 受 performance_schema_max_sql_text_length（默认 1024）
 * 截断，而 information_schema.PROCESSLIST 的 INFO 列保留<b>正在执行</b>语句的完整文本。
 * 采集轮次中先从 processlist 捕获"已运行超过 long_query_time 且仍在执行"的语句全文，
 * 按 (instanceId, threadId) 缓存；该语句执行结束进入 events_statements_history 后（通常为
 * 同轮或后续轮次），若其 SQL_TEXT 被目标库截断（以 "..." 结尾），则按前缀比对回填完整文本。
 *
 * <p>覆盖范围：执行期跨过采集点的长慢查询（最需要 EXPLAIN 的一批）；
 * 起止都落在两次采集之间的短慢查询无法捕获，仍为截断文本。
 */
@Component
public class FullSqlCaptureStore {

    /** 捕获记录保留时长：超过该时长未被消费则视为过期（语句早已结束且样本已入库）。 */
    private static final long TTL_MILLIS = 30 * 60_000L;

    /** 单实例缓存线程数上限（长慢查询并发本就有限，超限整体重置防膨胀）。 */
    private static final int MAX_THREADS_PER_INSTANCE = 1_000;

    private record Capture(String fullSql, long capturedAt) {
    }

    /** instanceId -> (threadId -> 捕获的完整 SQL)。 */
    private final Map<Long, Map<Long, Capture>> captures = new ConcurrentHashMap<>();

    /** 记录一次在途捕获（同线程重复捕获时保留最新一次）。 */
    public void put(long instanceId, long threadId, String fullSql) {
        long now = System.currentTimeMillis();
        Map<Long, Capture> byThread = captures.computeIfAbsent(instanceId, k -> new ConcurrentHashMap<>());
        if (byThread.size() > MAX_THREADS_PER_INSTANCE) {
            byThread.clear();
        }
        byThread.put(threadId, new Capture(fullSql, now));
        // 顺带惰性清理过期条目（量小，全量扫描代价可忽略）
        byThread.entrySet().removeIf(e -> now - e.getValue().capturedAt() > TTL_MILLIS);
    }

    /**
     * 按截断文本回查完整 SQL：截断文本须以 "..." 结尾，且去掉省略号后为缓存全文的前缀
     * （防止同线程后续语句错误匹配到先前捕获的全文）。匹配不到返回 null。
     */
    public String resolve(long instanceId, long threadId, String truncatedSql) {
        if (truncatedSql == null || !truncatedSql.endsWith("...")) {
            return null;
        }
        Map<Long, Capture> byThread = captures.get(instanceId);
        Capture c = byThread == null ? null : byThread.get(threadId);
        if (c == null || System.currentTimeMillis() - c.capturedAt() > TTL_MILLIS) {
            return null;
        }
        String prefix = truncatedSql.substring(0, truncatedSql.length() - 3);
        return c.fullSql().startsWith(prefix) ? c.fullSql() : null;
    }

    /** 实例删除/暂停时清理。 */
    public void evict(long instanceId) {
        captures.remove(instanceId);
    }
}
