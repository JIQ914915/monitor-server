-- 告警信息模板：
-- 1) 规则层新增 message_template（内置规则由系统提供默认模板）
-- 2) 事件层新增 alert_message（渲染快照，供页面展示与通知复用）

ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS message_template TEXT;

ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS alert_message TEXT;

-- 回填规则模板：内置规则固定模板；自定义规则留空时也给默认模板，便于直接生效
UPDATE alert_rule
SET message_template = CASE
    WHEN metric_name IS NOT NULL AND metric_name <> ''
        THEN '【{ruleName}】触发告警：实例 {instanceName}，指标 {metricName} 当前值 {triggerValue}，阈值 {thresholdValue}'
    ELSE '【{ruleName}】触发告警：当前值 {triggerValue}，阈值 {thresholdValue}，实例 {instanceName}'
END
WHERE message_template IS NULL OR btrim(message_template) = '';

-- 回填历史事件文案快照（已有事件可直接展示）
UPDATE alert_event
SET alert_message = '【' || COALESCE(rule_name, '') || '】触发告警：当前值 '
                    || COALESCE(trigger_value, '')
                    || '，阈值 '
                    || COALESCE(threshold_value, '')
                    || '，实例 '
                    || COALESCE(instance_name, '')
WHERE alert_message IS NULL OR btrim(alert_message) = '';
