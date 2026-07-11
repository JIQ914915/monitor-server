-- =============================================================
-- V148：告警规则 / 场景「一键开启常用」支持
--   1. alert_rule / monitor_scenario 增加 recommended 标记（系统推荐的常用集）
--   2. 种子：标记常用内置规则与常用内置场景
--      口径：覆盖可用性、容量写满、复制中断、核心性能瓶颈等
--      「不开就等于没监控」的关键项；基线波动、审计通知类等噪声敏感项不入选，
--      由用户按需手动开启。后续可直接改表调整常用集，无需发版。
-- 注：全脚本幂等
-- =============================================================

ALTER TABLE alert_rule
  ADD COLUMN IF NOT EXISTS recommended BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN alert_rule.recommended IS '系统推荐的常用规则：告警规则页「一键开启常用」的圈选范围';

ALTER TABLE monitor_scenario
  ADD COLUMN IF NOT EXISTS recommended BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN monitor_scenario.recommended IS '系统推荐的常用场景：场景管理页「一键开启常用」的圈选范围';

-- ---- 常用内置规则 ----
UPDATE alert_rule SET recommended = TRUE
 WHERE rule_code IN (
     -- 可用性（最高优先级）
     'builtin.availability',
     'builtin.instance.restarted',
     'builtin.conn.rejected.critical',
     -- 连接与并发
     'builtin.conn.usage.critical',
     'builtin.threads_running.critical',
     -- SQL 性能与锁
     'builtin.slow_queries.warning',
     'builtin.deadlock.warning',
     'builtin.lock_waits.critical',
     'builtin.trx_max_seconds.critical',
     -- 复制（含 8.0 拆分版本）
     'builtin.replication.io_stopped',
     'builtin.replication.sql_stopped',
     'builtin.replication.delay.critical',
     'builtin.replication.io_stopped.v8',
     'builtin.replication.sql_stopped.v8',
     'builtin.replication.delay.critical.v8',
     -- 容量与缓存
     'builtin.binlog.size.critical',
     'builtin.buffer_pool_hit_rate.critical',
     -- 主机（关联主机的实例适用）
     'builtin.host.unreachable',
     'builtin.host.disk.high',
     'builtin.host.disk.critical',
     'builtin.host.fs.readonly',
     'builtin.host.cpu.high',
     'builtin.host.mem.high'
 )
   AND recommended = FALSE;

-- ---- 常用内置场景 ----
UPDATE monitor_scenario SET recommended = TRUE
 WHERE scenario_code IN (
     'scenario.connection_pool_exhaustion',
     'scenario.sql_performance_degradation',
     'scenario.lock_contention',
     'scenario.replication_risk',
     'scenario.host_disk_exhaustion'
 )
   AND recommended = FALSE;
