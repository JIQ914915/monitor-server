-- 统一告警信息模板字段：
-- - 规则侧统一使用 condition_config.displayTemplate（内置/自定义一致）
-- - 移除 alert_rule.message_template（若已由 V75 引入）
-- - 事件侧保留 alert_event.alert_message 作为渲染快照

ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS alert_message TEXT;

-- 兼容已执行 V75 的环境：删除独立 message_template 列，避免与 condition_config 重复
ALTER TABLE alert_rule
    DROP COLUMN IF EXISTS message_template;

-- 为所有规则补齐 condition_config.displayTemplate 默认值
UPDATE alert_rule
SET condition_config = jsonb_set(
    COALESCE(condition_config, '{}'::jsonb),
    '{displayTemplate}',
    to_jsonb(CASE
        WHEN metric_name IS NOT NULL AND btrim(metric_name) <> ''
            THEN '【{ruleName}】触发告警：实例 {instanceName}，指标 {metricName} 当前值 {triggerValue}，阈值 {thresholdValue}'
        ELSE '【{ruleName}】触发告警：当前值 {triggerValue}，阈值 {thresholdValue}，实例 {instanceName}'
    END),
    true
)
WHERE
    condition_config IS NULL
    OR (condition_config ? 'displayTemplate') = false
    OR COALESCE(condition_config->>'displayTemplate', '') = '';

-- 回填历史事件消息快照（已有事件可直接用于页面展示与通知）
UPDATE alert_event
SET alert_message = '【' || COALESCE(rule_name, '') || '】触发告警：当前值 '
                    || COALESCE(trigger_value, '')
                    || '，阈值 '
                    || COALESCE(threshold_value, '')
                    || '，实例 '
                    || COALESCE(instance_name, '')
WHERE alert_message IS NULL OR btrim(alert_message) = '';
