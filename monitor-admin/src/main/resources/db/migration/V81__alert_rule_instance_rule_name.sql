-- =============================================================
-- V81：内置规则实例级存储规则名称（只读）
-- =============================================================

ALTER TABLE alert_rule_instance_config
    ADD COLUMN IF NOT EXISTS rule_name VARCHAR(100);

COMMENT ON COLUMN alert_rule_instance_config.rule_name IS '实例级规则名称快照（内置规则启用时复制模板，禁止用户修改）';

UPDATE alert_rule_instance_config cfg
SET rule_name = ar.rule_name,
    updated_at = now()
FROM alert_rule ar
WHERE cfg.rule_id = ar.id
  AND ar.rule_type = 'builtin'
  AND (cfg.rule_name IS NULL OR trim(cfg.rule_name) = '');
