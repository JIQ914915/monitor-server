-- =============================================================
-- 连接失败告警内置化：从告警规则体系移除 builtin.availability
--   新逻辑由采集程序直接承载（ConnectionFailureAlertService）：
--   实例连续 3 次连接失败标 abnormal 时建一级告警事件并全渠道通知，
--   连接恢复 normal 时事件自动恢复并发送恢复通知。
--   规则编码改为 system.connection_failure（仅事件/通知记录引用，无规则行）。
-- =============================================================

-- 1. 关闭该规则遗留的活跃事件（历史事件保留，不删除）
UPDATE alert_event
   SET status      = 'closed',
       last_remark = '可用性规则下线（连接失败告警已内置到采集逻辑），系统联动关闭',
       updated_at  = now()
 WHERE status IN ('pending', 'confirmed', 'handling')
   AND rule_id IN (SELECT id FROM alert_rule WHERE rule_code = 'builtin.availability');

-- 2. 清理该规则的触发/恢复持续窗口
DELETE FROM alert_evaluate_window
 WHERE dedup_key LIKE 'builtin.availability:%';

-- 3. 清理实例级启停/参数覆盖配置
DELETE FROM alert_rule_instance_config
 WHERE rule_code = 'builtin.availability';

-- 4. 删除规则模板（alert_rule_metric_ref 随 FK 级联删除）
DELETE FROM alert_rule
 WHERE rule_code = 'builtin.availability';

-- 5. 系统内置连接失败事件不再对应 alert_rule 行，rule_id 允许为空
ALTER TABLE alert_event ALTER COLUMN rule_id DROP NOT NULL;
COMMENT ON COLUMN alert_event.rule_id IS '规则ID（系统内置事件如连接失败为 NULL，规则信息以 rule_name/rule_level 快照为准）';
