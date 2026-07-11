-- =============================================================
-- V58：补充内置告警规则 P0 集合（需求 §11.5 内置 P0 规则建议）
--
-- 注意：
--   1. alertEvaluateJobHandler 当前仅查 metric_data_1m；
--      标注 [1m] 的规则可立即生效，标注 [1h]/[1d] 的规则需评估引擎
--      后续扩展为多粒度查询后方能触发，但数据插入不影响规则管理页面显示。
--   2. condition_config/recovery_config 格式：{"operator":">=","threshold":80.0}
--      与 AlertEvaluateJobHandler.checkCondition() 对应。
--   3. 所有规则 scope_type='all'，作用于全部非暂停实例；
--      实例级页面筛选由前端按 scope_config.instanceIds 附加，
--      当前作用于"全局"，各实例独立触发/恢复事件。
--   4. 规则已按 rule_code 唯一约束做幂等处理（INSERT … ON CONFLICT DO NOTHING）。
-- =============================================================

-- ============================================================
-- 一、连接类
-- ============================================================

-- [1] 连接使用率过高（warning）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '连接使用率过高',
    'builtin.conn.usage.warning',
    'builtin', 'warning', 'connection', 'mysql.conn.usage',
    '{"operator":">=","threshold":80.0}',
    '{"operator":"<","threshold":70.0}',
    'all', NULL, NULL, TRUE, 'system',
    '连接使用率 ≥ 80% 时触发预警，提醒 DBA 关注连接池使用情况'
) ON CONFLICT (rule_code) DO NOTHING;

-- [2] 连接使用率严重（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '连接使用率严重',
    'builtin.conn.usage.critical',
    'builtin', 'critical', 'connection', 'mysql.conn.usage',
    '{"operator":">=","threshold":90.0}',
    '{"operator":"<","threshold":80.0}',
    'all', NULL, NULL, TRUE, 'system',
    '连接使用率 ≥ 90% 时触发严重告警，可能导致新连接被拒绝'
) ON CONFLICT (rule_code) DO NOTHING;

-- [3] 活跃线程数过高（warning）[1m]
--   Threads_running 反映当前活跃执行线程数，持续偏高通常伴随 SQL 慢查询或锁阻塞
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '活跃线程数过高',
    'builtin.threads_running.warning',
    'builtin', 'warning', 'connection', 'mysql.status.threads_running',
    '{"operator":">=","threshold":50.0}',
    '{"operator":"<","threshold":30.0}',
    'all', NULL, NULL, TRUE, 'system',
    '活跃线程数（Threads_running）≥ 50 时触发预警，可能存在慢查询或锁等待'
) ON CONFLICT (rule_code) DO NOTHING;

-- [4] 活跃线程数严重（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '活跃线程数严重',
    'builtin.threads_running.critical',
    'builtin', 'critical', 'connection', 'mysql.status.threads_running',
    '{"operator":">=","threshold":100.0}',
    '{"operator":"<","threshold":70.0}',
    'all', NULL, NULL, TRUE, 'system',
    '活跃线程数（Threads_running）≥ 100 时触发严重告警，实例面临显著性能压力'
) ON CONFLICT (rule_code) DO NOTHING;

-- ============================================================
-- 二、性能类
-- ============================================================

-- [5] 慢 SQL 增量异常（warning）[1m]
--   mysql.delta.slow_queries = 本采集周期内新增慢查询数（GlobalStatusItem 产出）
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '慢 SQL 增量异常',
    'builtin.slow_queries.warning',
    'builtin', 'warning', 'performance', 'mysql.delta.slow_queries',
    '{"operator":">=","threshold":50.0}',
    '{"operator":"<","threshold":20.0}',
    'all', NULL, NULL, TRUE, 'system',
    '单采集周期内慢查询增量 ≥ 50 条时触发预警，建议排查高耗时 SQL 及索引'
) ON CONFLICT (rule_code) DO NOTHING;

-- [6] Buffer Pool 命中率偏低（warning）[1m]
--   命中率持续低于阈值说明 InnoDB 内存吃紧，物理 IO 增多
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    'Buffer Pool 命中率偏低',
    'builtin.buffer_pool_hit_rate.warning',
    'builtin', 'warning', 'performance', 'mysql.innodb.buffer_pool_hit_rate',
    '{"operator":"<","threshold":95.0}',
    '{"operator":">=","threshold":97.0}',
    'all', NULL, NULL, TRUE, 'system',
    'InnoDB Buffer Pool 命中率低于 95% 时触发预警，物理 IO 增多可能影响性能'
) ON CONFLICT (rule_code) DO NOTHING;

-- [7] Buffer Pool 命中率严重（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    'Buffer Pool 命中率严重',
    'builtin.buffer_pool_hit_rate.critical',
    'builtin', 'critical', 'performance', 'mysql.innodb.buffer_pool_hit_rate',
    '{"operator":"<","threshold":90.0}',
    '{"operator":">=","threshold":93.0}',
    'all', NULL, NULL, TRUE, 'system',
    'InnoDB Buffer Pool 命中率低于 90% 时触发严重告警，建议检查内存配置与热数据量'
) ON CONFLICT (rule_code) DO NOTHING;

-- ============================================================
-- 三、稳定性类（锁与事务）
-- ============================================================

-- [8] 行锁等待过高（warning）[1m]
--   mysql.innodb.lock_waits = 当前锁等待关系总数（LockWaitsItem 产出）
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '行锁等待过高',
    'builtin.lock_waits.warning',
    'builtin', 'warning', 'performance', 'mysql.innodb.lock_waits',
    '{"operator":">=","threshold":5.0}',
    '{"operator":"<","threshold":3.0}',
    'all', NULL, NULL, TRUE, 'system',
    '当前行锁等待关系数 ≥ 5 时触发预警，建议定位持锁会话与长事务'
) ON CONFLICT (rule_code) DO NOTHING;

-- [9] 行锁等待严重（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '行锁等待严重',
    'builtin.lock_waits.critical',
    'builtin', 'critical', 'performance', 'mysql.innodb.lock_waits',
    '{"operator":">=","threshold":20.0}',
    '{"operator":"<","threshold":10.0}',
    'all', NULL, NULL, TRUE, 'system',
    '当前行锁等待关系数 ≥ 20 时触发严重告警，可能发生严重锁阻塞，影响业务写入'
) ON CONFLICT (rule_code) DO NOTHING;

-- [10] 长事务风险（warning）[1m]
--   mysql.innodb.trx_max_seconds = 当前最长活跃事务持续秒数（InnodbTrxItem 产出）
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '长事务风险',
    'builtin.trx_max_seconds.warning',
    'builtin', 'warning', 'performance', 'mysql.innodb.trx_max_seconds',
    '{"operator":">=","threshold":60.0}',
    '{"operator":"<","threshold":30.0}',
    'all', NULL, NULL, TRUE, 'system',
    '存在持续时间 ≥ 60 秒的活跃事务，可能造成 undo 积压与 MVCC 读放大'
) ON CONFLICT (rule_code) DO NOTHING;

-- [11] 长事务严重（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '长事务严重',
    'builtin.trx_max_seconds.critical',
    'builtin', 'critical', 'performance', 'mysql.innodb.trx_max_seconds',
    '{"operator":">=","threshold":300.0}',
    '{"operator":"<","threshold":120.0}',
    'all', NULL, NULL, TRUE, 'system',
    '存在持续时间 ≥ 5 分钟的活跃事务，undo 积压严重，须立即定位并评估回滚'
) ON CONFLICT (rule_code) DO NOTHING;

-- ============================================================
-- 四、可用性类（复制）
-- ============================================================

-- [12] 复制 IO 线程停止（critical）[1m]
--   mysql.replication.io_running = 0 表示 IO 线程停止（仅从库有此指标）
--   主库无复制配置时该指标不在 metric_data_1m，evaluator 返回 null → NORMAL，不触发误报
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '复制 IO 线程停止',
    'builtin.replication.io_stopped',
    'builtin', 'critical', 'availability', 'mysql.replication.io_running',
    '{"operator":"<","threshold":1.0}',
    '{"operator":">=","threshold":1.0}',
    'all', NULL, NULL, TRUE, 'system',
    '从库复制 IO 线程停止（Slave_IO_Running=No），主从同步中断，立即检查网络与主库状态'
) ON CONFLICT (rule_code) DO NOTHING;

-- [13] 复制 SQL 线程停止（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '复制 SQL 线程停止',
    'builtin.replication.sql_stopped',
    'builtin', 'critical', 'availability', 'mysql.replication.sql_running',
    '{"operator":"<","threshold":1.0}',
    '{"operator":">=","threshold":1.0}',
    'all', NULL, NULL, TRUE, 'system',
    '从库复制 SQL 线程停止（Slave_SQL_Running=No），回放中断，检查复制错误并确认是否需要跳过'
) ON CONFLICT (rule_code) DO NOTHING;

-- [14] 复制延迟过高（warning）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '复制延迟过高',
    'builtin.replication.delay.warning',
    'builtin', 'warning', 'availability', 'mysql.replication.seconds_behind',
    '{"operator":">=","threshold":30.0}',
    '{"operator":"<","threshold":10.0}',
    'all', NULL, NULL, TRUE, 'system',
    '主从复制延迟 ≥ 30 秒时触发预警，检查主库写入量与从库回放能力'
) ON CONFLICT (rule_code) DO NOTHING;

-- [15] 复制延迟严重（critical）[1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    '复制延迟严重',
    'builtin.replication.delay.critical',
    'builtin', 'critical', 'availability', 'mysql.replication.seconds_behind',
    '{"operator":">=","threshold":120.0}',
    '{"operator":"<","threshold":60.0}',
    'all', NULL, NULL, TRUE, 'system',
    '主从复制延迟 ≥ 120 秒时触发严重告警，从库数据严重落后，请立即处理'
) ON CONFLICT (rule_code) DO NOTHING;

-- ============================================================
-- 五、资源类（小时级指标，需评估引擎后续扩展为多粒度查询）
-- ============================================================

-- [16] binlog 占用过高（warning）[1h]
--   mysql.binlog.total_bytes = 全部 binlog 文件总字节（BinlogStatusItem，小时级）
--   当前 alertEvaluateJobHandler 仅查 metric_data_1m，此规则待评估引擎支持 1h 表后生效
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    'binlog 占用过高',
    'builtin.binlog.size.warning',
    'builtin', 'warning', 'resource', 'mysql.binlog.total_bytes',
    '{"operator":">=","threshold":10737418240.0}',
    '{"operator":"<","threshold":8589934592.0}',
    'all', NULL, NULL, TRUE, 'system',
    'binlog 总占用 ≥ 10 GB 时触发预警（阈值可按实际磁盘容量调整）'
) ON CONFLICT (rule_code) DO NOTHING;

-- [17] binlog 占用严重（critical）[1h]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, scope_config, notification_config,
    enabled, created_by, description)
VALUES (
    'binlog 占用严重',
    'builtin.binlog.size.critical',
    'builtin', 'critical', 'resource', 'mysql.binlog.total_bytes',
    '{"operator":">=","threshold":21474836480.0}',
    '{"operator":"<","threshold":16106127360.0}',
    'all', NULL, NULL, TRUE, 'system',
    'binlog 总占用 ≥ 20 GB 时触发严重告警，建议立即清理或调整 expire_logs_days'
) ON CONFLICT (rule_code) DO NOTHING;

-- ============================================================
-- 补充：回填 V48 已有规则的 category / description 字段
-- ============================================================
UPDATE alert_rule
SET category    = 'availability',
    description = '实例无法连接时立即触发严重告警（由采集程序在连接失败时写入 availability=0）'
WHERE rule_code = 'builtin.availability'
  AND category IS NULL;

-- ============================================================
-- 确认：所有内置规则均已通过 ON CONFLICT DO NOTHING 幂等写入
-- 统计：V58 新增 17 条规则（含 V48 已有的 builtin.availability = 1 条，共 18 条）
-- ============================================================
