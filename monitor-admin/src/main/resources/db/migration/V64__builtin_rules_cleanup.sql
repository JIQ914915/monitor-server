-- =============================================================
-- V64：内置告警规则整理
--
--   一、删除"成对"规则中的低阈值规则（共 8 条 .warning），
--       每个指标只保留较严格的高阈值规则
--   二、修复旧规则的作用域：scope_type='all' → 'db_type'，
--       并绑定 MySQL 数据库类型（db_type_id）
--   三、精简规则名称：去掉"严重"等旧级别词汇，改为阈值描述
--   四、高危规则升为一级：实例不可达、复制线程停止
-- =============================================================

-- ================================================================
-- 一、删除低阈值重复规则
-- ================================================================
DELETE FROM alert_rule WHERE rule_code IN (
    'builtin.conn.usage.warning',           -- 保留 .critical（≥90%）
    'builtin.threads_running.warning',      -- 保留 .critical（≥100）
    'builtin.buffer_pool_hit_rate.warning', -- 保留 .critical（<90%）
    'builtin.lock_waits.warning',           -- 保留 .critical（≥20）
    'builtin.trx_max_seconds.warning',      -- 保留 .critical（≥300s）
    'builtin.replication.delay.warning',    -- 保留 .critical（≥120s）
    'builtin.binlog.size.warning',          -- 保留 .critical（≥20GB）
    'builtin.errorlog.error.warning'        -- 保留 .critical（≥50条）
);

-- 清理被删规则遗留的实例级配置记录
DELETE FROM alert_rule_instance_config
WHERE rule_id NOT IN (SELECT id FROM alert_rule);

-- ================================================================
-- 二、修复旧规则 scope_type：all → db_type（绑定 MySQL）
--     V60 之后服务层仅读取 scope_type='db_type' 规则，
--     V48/V59 的 'all' 规则目前完全不可见，此处统一修正
-- ================================================================
UPDATE alert_rule
SET    scope_type = 'db_type',
       db_type_id = (SELECT id FROM database_type WHERE label = 'MySQL' LIMIT 1)
WHERE  rule_type  = 'builtin'
  AND  scope_type = 'all';

-- ================================================================
-- 三、精简保留规则的名称与描述
-- ================================================================
UPDATE alert_rule
SET    rule_name  = '连接使用率过高',
       description = '连接使用率 ≥ 90% 时告警，可能导致新连接被拒绝（阈值可按实例规格调整）'
WHERE  rule_code = 'builtin.conn.usage.critical';

UPDATE alert_rule
SET    rule_name  = '活跃线程数过多',
       description = '活跃线程数（Threads_running）≥ 100，实例面临显著性能压力，请排查慢查询或锁等待'
WHERE  rule_code = 'builtin.threads_running.critical';

UPDATE alert_rule
SET    rule_name  = 'Buffer Pool 命中率低',
       description = 'InnoDB Buffer Pool 命中率低于 90%，物理 IO 增多，建议检查内存配置与热数据量'
WHERE  rule_code = 'builtin.buffer_pool_hit_rate.critical';

UPDATE alert_rule
SET    rule_name  = '行锁等待过多',
       description = '行锁等待关系数 ≥ 20，可能发生严重锁阻塞，影响业务写入，建议定位持锁会话'
WHERE  rule_code = 'builtin.lock_waits.critical';

UPDATE alert_rule
SET    rule_name  = '长事务阻塞',
       description = '存在持续时间 ≥ 5 分钟的活跃事务，undo 积压严重，须立即定位并评估回滚'
WHERE  rule_code = 'builtin.trx_max_seconds.critical';

UPDATE alert_rule
SET    rule_name  = '主从复制延迟过高',
       description = '主从复制延迟 ≥ 120 秒，从库数据严重落后，请立即处理'
WHERE  rule_code = 'builtin.replication.delay.critical';

UPDATE alert_rule
SET    rule_name  = 'binlog 空间占用过高',
       description = 'binlog 总占用 ≥ 20 GB，建议立即清理或调整 expire_logs_days'
WHERE  rule_code = 'builtin.binlog.size.critical';

UPDATE alert_rule
SET    rule_name  = '错误日志错误数过多',
       description = '单采集周期内 performance_schema 记录的 ERROR 级别事件数 ≥ 50（需 MySQL 5.7+）'
WHERE  rule_code = 'builtin.errorlog.error.critical';

-- ================================================================
-- 四、高危规则升为一级（实例不可达、复制线程停止属于 P0 事件）
-- ================================================================
UPDATE alert_rule
SET    rule_level = 'level_1'
WHERE  rule_code IN (
    'builtin.availability',
    'builtin.replication.io_stopped',
    'builtin.replication.sql_stopped'
);

-- ================================================================
-- 验证：最终内置规则一览
-- ================================================================
-- SELECT rule_code, rule_name, rule_level, scope_type, db_type_id
-- FROM alert_rule WHERE rule_type = 'builtin' ORDER BY category, rule_level;
