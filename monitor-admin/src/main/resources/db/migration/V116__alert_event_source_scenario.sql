-- =============================================================
-- V116：alert_event 扩展场景综合事件字段
--   event_source     事件来源（字典 event_source：rule/scenario/system）
--   scenario_code    场景来源事件回链 monitor_scenario.scenario_code
--   signals_snapshot 触发时各信号快照（供事件详情还原触发时刻的信号状态）
-- 注：幂等（IF NOT EXISTS），兼容曾以旧编号执行过本内容的开发库。
-- =============================================================

ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS event_source     VARCHAR(20) NOT NULL DEFAULT 'rule',
    ADD COLUMN IF NOT EXISTS scenario_code    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS signals_snapshot JSONB;

COMMENT ON COLUMN alert_event.event_source IS '事件来源（字典 event_source）：rule=告警规则 / scenario=场景综合诊断 / system=系统事件';
COMMENT ON COLUMN alert_event.scenario_code IS '场景来源事件对应的 monitor_scenario.scenario_code，其余来源为空';
COMMENT ON COLUMN alert_event.signals_snapshot IS '场景事件触发时各信号快照：[{"code","name","expr","currentVal","met"}]，诊断结论存 alert_message';

-- 存量回填：rule_id 为空的事件为系统事件（连接失败告警，V107 起 rule_id 置空）
UPDATE alert_event SET event_source = 'system' WHERE rule_id IS NULL;
