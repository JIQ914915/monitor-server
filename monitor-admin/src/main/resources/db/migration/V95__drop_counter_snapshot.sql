-- 计数器基线策略统一为节点内存（重启后首个周期跳过速率/增量指标），
-- 不再持久化 counter_snapshot，删除该表。
DROP TABLE IF EXISTS counter_snapshot;
