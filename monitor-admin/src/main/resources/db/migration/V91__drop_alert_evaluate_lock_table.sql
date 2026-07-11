-- =============================================================
-- V91：告警评估互斥锁改用 Redisson（Redis 分布式锁），废弃基于表的伪锁实现
--   原 alert_evaluate_lock 表依赖 INSERT ... ON CONFLICT DO NOTHING + TTL 轮询清理，
--   在高并发/多节点场景下存在数据库写入压力大、无法及时释放等问题；
--   现改为 monitor-collector 内 DistributedLockService（Redisson RLock + TTL 自动过期）。
-- =============================================================
DROP TABLE IF EXISTS alert_evaluate_lock;
