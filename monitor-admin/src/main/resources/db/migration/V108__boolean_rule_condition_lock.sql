-- =============================================================
-- 布尔型内置规则条件固化与友好展示
--   复制 IO/SQL 线程停止、MGR 成员状态异常这类规则的指标是 0/1 布尔值，
--   "< 1" 的机器语义让用户困惑，且阈值本身没有调整意义。
--   处理：
--     1. condition_config 增加 conditionType='boolean'（条件锁定标记，
--        后端据此拒绝条件类覆盖，前端据此收敛可编辑项为：
--        告警级别 / 扫描间隔 / 通知设置 / 启用停用）；
--     2. condition_config / recovery_config 增加 displayText 友好描述，
--        页面用它替代 "< 1" 展示触发/恢复条件；
--     3. 清理这批规则历史实例覆盖中的条件覆盖（阈值类字段对布尔规则无意义）。
-- =============================================================

-- 复制 IO 线程停止
UPDATE alert_rule
   SET condition_config = condition_config
       || '{"conditionType":"boolean","displayText":"检测到复制 IO 线程停止（Slave_IO_Running = No）时立即触发"}'::jsonb,
       recovery_config  = COALESCE(recovery_config, '{}'::jsonb)
       || '{"displayText":"复制 IO 线程恢复运行（Slave_IO_Running = Yes）时自动恢复"}'::jsonb
 WHERE rule_code = 'builtin.replication.io_stopped';

-- 复制 SQL 线程停止
UPDATE alert_rule
   SET condition_config = condition_config
       || '{"conditionType":"boolean","displayText":"检测到复制 SQL 线程停止（Slave_SQL_Running = No）时立即触发"}'::jsonb,
       recovery_config  = COALESCE(recovery_config, '{}'::jsonb)
       || '{"displayText":"复制 SQL 线程恢复运行（Slave_SQL_Running = Yes）时自动恢复"}'::jsonb
 WHERE rule_code = 'builtin.replication.sql_stopped';

-- MGR 成员状态异常
UPDATE alert_rule
   SET condition_config = condition_config
       || '{"conditionType":"boolean","displayText":"检测到 MGR 成员状态异常（OFFLINE / ERROR）时立即触发"}'::jsonb,
       recovery_config  = COALESCE(recovery_config, '{}'::jsonb)
       || '{"displayText":"MGR 成员状态恢复 ONLINE 时自动恢复"}'::jsonb
 WHERE rule_code = 'builtin.gr.member_state.critical';

-- 清理历史实例覆盖中的条件类字段：布尔规则的条件由系统固化，
-- 旧覆盖（改过阈值/运算符）继续生效会与"不可修改"的约束冲突
UPDATE alert_rule_instance_config
   SET condition_config = NULL,
       recovery_config  = NULL
 WHERE rule_type = 'builtin'
   AND rule_code IN (
       'builtin.replication.io_stopped',
       'builtin.replication.sql_stopped',
       'builtin.gr.member_state.critical'
   );
