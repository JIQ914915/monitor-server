-- 仅保留“自定义规则 + 多值模式”的 displayTemplate
-- 其余规则（内置规则、自定义单值模式）统一由系统按规则拼装告警文案

UPDATE alert_rule
SET condition_config = condition_config - 'displayTemplate'
WHERE NOT (
    rule_type = 'custom'
    AND COALESCE(condition_config->>'resultMode', '') = 'multi'
);

UPDATE alert_rule
SET condition_config = jsonb_set(
    COALESCE(condition_config, '{}'::jsonb),
    '{displayTemplate}',
    to_jsonb('监控对象{dimensionKey}当前值{triggerValue}，超过阈值{thresholdValue}'::text),
    true
)
WHERE
    rule_type = 'custom'
    AND COALESCE(condition_config->>'resultMode', '') = 'multi'
    AND (
      (condition_config ? 'displayTemplate') = false
      OR COALESCE(condition_config->>'displayTemplate', '') = ''
    );
