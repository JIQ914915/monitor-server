-- =============================================================
-- V112：复制类内置规则按版本拆分（5.6/5.7 与 8.0/8.4 两套）
--
-- 背景：
--   复制 IO 线程停止 / 复制 SQL 线程停止 / 主从复制延迟过高 三条规则
--   此前为全版本共用一套文案。而 5.x 与 8.0 的复制术语不同：
--     5.6/5.7 : SHOW SLAVE STATUS   → Slave_IO_Running / Slave_SQL_Running / Seconds_Behind_Master
--     8.0/8.4 : SHOW REPLICA STATUS → Replica_IO_Running / Replica_SQL_Running / Seconds_Behind_Source
--   采集端已归一化为统一指标（mysql.replication.*），评估逻辑不受影响，
--   本脚本只拆分模板并按版本呈现正确术语。
--
-- 做法：
--   1. 存量三条规则收窄为 5.6/5.7 专用（rule_code 不变，5.x 实例配置无感）；
--   2. 复制出三条 8.0/8.4 专用规则（rule_code 加 .v8 后缀），文案改用 Replica_*/Source 术语；
--   3. 8.0/8.4 实例的存量启停配置与告警事件迁移到新规则。
-- =============================================================

-- ---- 1. 存量规则收窄为 5.6/5.7，描述补充 5.x 术语 ----
UPDATE alert_rule
   SET db_version_ids = (SELECT jsonb_agg(id ORDER BY id) FROM database_version
                          WHERE db_type = 'mysql' AND version_code IN ('5.6', '5.7')),
       updated_at = now()
 WHERE rule_code IN (
     'builtin.replication.io_stopped',
     'builtin.replication.sql_stopped',
     'builtin.replication.delay.critical'
 );

UPDATE alert_rule
   SET description = '从库复制 IO 线程停止（Slave_IO_Running = No），无法从主库拉取 binlog，检查网络与主库连接（MySQL 5.6/5.7）'
 WHERE rule_code = 'builtin.replication.io_stopped';

UPDATE alert_rule
   SET description = '从库复制 SQL 线程停止（Slave_SQL_Running = No），回放中断，检查复制错误并确认是否需要跳过（MySQL 5.6/5.7）'
 WHERE rule_code = 'builtin.replication.sql_stopped';

UPDATE alert_rule
   SET description = '主从复制延迟（Seconds_Behind_Master）≥ 120 秒，从库数据严重落后，请立即处理（MySQL 5.6/5.7）'
 WHERE rule_code = 'builtin.replication.delay.critical';

-- ---- 2. 复制出 8.0/8.4 专用规则（沿用级别/阈值/通知，替换术语文案）----
-- 复制 IO 线程停止（8.0+）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
SELECT rule_name,
       'builtin.replication.io_stopped.v8',
       rule_level, db_type_id,
       (SELECT jsonb_agg(id ORDER BY id) FROM database_version
         WHERE db_type = 'mysql' AND version_code IN ('8.0', '8.4')),
       metric_name,
       condition_config
           || '{"displayText":"检测到复制 IO 线程停止（Replica_IO_Running = No）时立即触发"}'::jsonb,
       COALESCE(recovery_config, '{}'::jsonb)
           || '{"displayText":"复制 IO 线程恢复运行（Replica_IO_Running = Yes）时自动恢复"}'::jsonb,
       notification_config,
       scan_interval_min, scan_interval_source, 'system',
       '从库复制 IO 线程停止（Replica_IO_Running = No），无法从源库拉取 binlog，检查网络与源库连接（MySQL 8.0+）'
  FROM alert_rule
 WHERE rule_code = 'builtin.replication.io_stopped'
ON CONFLICT (rule_code) DO NOTHING;

-- 复制 SQL 线程停止（8.0+）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
SELECT rule_name,
       'builtin.replication.sql_stopped.v8',
       rule_level, db_type_id,
       (SELECT jsonb_agg(id ORDER BY id) FROM database_version
         WHERE db_type = 'mysql' AND version_code IN ('8.0', '8.4')),
       metric_name,
       condition_config
           || '{"displayText":"检测到复制 SQL 线程停止（Replica_SQL_Running = No）时立即触发"}'::jsonb,
       COALESCE(recovery_config, '{}'::jsonb)
           || '{"displayText":"复制 SQL 线程恢复运行（Replica_SQL_Running = Yes）时自动恢复"}'::jsonb,
       notification_config,
       scan_interval_min, scan_interval_source, 'system',
       '从库复制 SQL 线程停止（Replica_SQL_Running = No），回放中断，检查复制错误并确认是否需要跳过（MySQL 8.0+）'
  FROM alert_rule
 WHERE rule_code = 'builtin.replication.sql_stopped'
ON CONFLICT (rule_code) DO NOTHING;

-- 主从复制延迟过高（8.0+，数值型规则，无 displayText）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
SELECT rule_name,
       'builtin.replication.delay.critical.v8',
       rule_level, db_type_id,
       (SELECT jsonb_agg(id ORDER BY id) FROM database_version
         WHERE db_type = 'mysql' AND version_code IN ('8.0', '8.4')),
       metric_name,
       condition_config, recovery_config, notification_config,
       scan_interval_min, scan_interval_source, 'system',
       '主从复制延迟（Seconds_Behind_Source）≥ 120 秒，从库数据严重落后，请立即处理（MySQL 8.0+）'
  FROM alert_rule
 WHERE rule_code = 'builtin.replication.delay.critical'
ON CONFLICT (rule_code) DO NOTHING;

-- ---- 3. 新规则的指标关联 ----
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN (
     'builtin.replication.io_stopped.v8',
     'builtin.replication.sql_stopped.v8',
     'builtin.replication.delay.critical.v8'
 )
ON CONFLICT (rule_id, metric_code) DO NOTHING;

-- ---- 4. 8.0/8.4 实例的存量启停配置迁移到新规则 ----
--   rule_code 加 .v8 后缀即可（阈值/级别/通知等覆盖字段原样保留）
UPDATE alert_rule_instance_config c
   SET rule_code = c.rule_code || '.v8',
       updated_at = now()
  FROM db_instance i
 WHERE c.instance_id = i.id
   AND c.rule_type = 'builtin'
   AND c.rule_code IN (
       'builtin.replication.io_stopped',
       'builtin.replication.sql_stopped',
       'builtin.replication.delay.critical'
   )
   AND i.db_version_id IN (SELECT id FROM database_version
                            WHERE db_type = 'mysql' AND version_code IN ('8.0', '8.4'));

-- ---- 5. 8.0/8.4 实例的告警事件迁移到新规则 ----
--   活跃事件必须迁移（否则旧规则不再匹配实例，事件无法自动恢复）；
--   历史事件一并迁移 rule_id/dedup_key，保持事件与规则的关联可追溯，
--   历史消息文案保持发送时原样，不做改写。
UPDATE alert_event e
   SET rule_id   = nr.id,
       dedup_key = replace(e.dedup_key, orule.rule_code || ':', nr.rule_code || ':')
  FROM alert_rule orule
  JOIN alert_rule nr ON nr.rule_code = orule.rule_code || '.v8'
     , db_instance i
 WHERE orule.rule_code IN (
       'builtin.replication.io_stopped',
       'builtin.replication.sql_stopped',
       'builtin.replication.delay.critical'
   )
   AND e.rule_id = orule.id
   AND e.instance_id = i.id
   AND i.db_version_id IN (SELECT id FROM database_version
                            WHERE db_type = 'mysql' AND version_code IN ('8.0', '8.4'));
