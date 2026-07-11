-- =============================================================
-- V83：alert_rule_instance_config 由 rule_id 关联切换为 rule_code 关联
-- 1) 新增 rule_code 并回填（内置=模板 rule_code，自定义=custom.<uuid-like>）
-- 2) 替换唯一约束：(rule_id, instance_id) -> (rule_code, instance_id)
-- 3) 去除 rule_id 外键及列，彻底改用 rule_code
-- =============================================================

ALTER TABLE alert_rule_instance_config
    ADD COLUMN IF NOT EXISTS rule_code VARCHAR(100);

-- 内置规则：根据旧 rule_id 回填 rule_code
UPDATE alert_rule_instance_config cfg
SET rule_code = ar.rule_code
FROM alert_rule ar
WHERE cfg.rule_code IS NULL
  AND cfg.rule_id IS NOT NULL
  AND cfg.rule_id = ar.id;

-- 自定义规则：生成稳定前缀 + 随机后缀
UPDATE alert_rule_instance_config cfg
SET rule_code = 'custom.' || md5(random()::text || clock_timestamp()::text || cfg.id::text)
WHERE cfg.rule_code IS NULL
  AND cfg.rule_type = 'custom';

-- 兜底：若仍为空，统一补随机码
UPDATE alert_rule_instance_config cfg
SET rule_code = 'rule.' || md5(random()::text || clock_timestamp()::text || cfg.id::text)
WHERE cfg.rule_code IS NULL;

ALTER TABLE alert_rule_instance_config
    ALTER COLUMN rule_code SET NOT NULL;

-- 替换旧唯一约束
ALTER TABLE alert_rule_instance_config
    DROP CONSTRAINT IF EXISTS uk_rule_instance;

ALTER TABLE alert_rule_instance_config
    ADD CONSTRAINT uk_rule_code_instance UNIQUE (rule_code, instance_id);

CREATE INDEX IF NOT EXISTS idx_rule_inst_cfg_rule_code
    ON alert_rule_instance_config (rule_code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_inst_cfg_custom_rule_code
    ON alert_rule_instance_config (rule_code)
    WHERE rule_type = 'custom';

-- 移除旧 rule_id 关联
ALTER TABLE alert_rule_instance_config
    DROP CONSTRAINT IF EXISTS alert_rule_instance_config_rule_id_fkey;

ALTER TABLE alert_rule_instance_config
    DROP COLUMN IF EXISTS rule_id;
