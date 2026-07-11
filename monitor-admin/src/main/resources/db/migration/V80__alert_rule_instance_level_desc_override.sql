-- =============================================================
-- V80：内置规则实例级告警级别与描述覆盖
-- =============================================================

ALTER TABLE alert_rule_instance_config
    ADD COLUMN IF NOT EXISTS rule_level VARCHAR(20),
    ADD COLUMN IF NOT EXISTS description VARCHAR(500);

COMMENT ON COLUMN alert_rule_instance_config.rule_level IS '实例级告警级别覆盖（为空继承模板）';
COMMENT ON COLUMN alert_rule_instance_config.description IS '实例级规则描述覆盖（为空继承模板）';

-- 回填：已有实例配置先继承模板值，避免展示为空
UPDATE alert_rule_instance_config cfg
SET rule_level = COALESCE(cfg.rule_level, ar.rule_level),
    description = COALESCE(cfg.description, ar.description),
    updated_at = now()
FROM alert_rule ar
WHERE cfg.rule_id = ar.id
  AND ar.rule_type = 'builtin';
