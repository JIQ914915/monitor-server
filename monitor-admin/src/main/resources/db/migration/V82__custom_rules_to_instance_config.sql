-- =============================================================
-- V82：自定义规则迁移到实例配置表，并清理 alert_rule 作用域字段
-- =============================================================

ALTER TABLE alert_rule_instance_config
    ALTER COLUMN rule_id DROP NOT NULL;

ALTER TABLE alert_rule_instance_config
    ADD COLUMN IF NOT EXISTS rule_type VARCHAR(20);

UPDATE alert_rule_instance_config
SET rule_type = COALESCE(rule_type, 'builtin')
WHERE rule_type IS NULL;

ALTER TABLE alert_rule_instance_config
    ALTER COLUMN rule_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_rule_inst_cfg_rule_type'
    ) THEN
        ALTER TABLE alert_rule_instance_config
            ADD CONSTRAINT ck_rule_inst_cfg_rule_type
            CHECK (rule_type IN ('builtin', 'custom'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_rule_inst_cfg_instance_type
    ON alert_rule_instance_config (instance_id, rule_type);

-- 自定义规则迁移：按 scope_config.instanceIds 拆分到实例配置
INSERT INTO alert_rule_instance_config (
    rule_id,
    instance_id,
    rule_type,
    enabled,
    rule_name,
    rule_level,
    description,
    metric_name,
    scan_interval_min,
    condition_config,
    recovery_config,
    notification_config,
    created_at,
    updated_at
)
SELECT
    NULL::bigint AS rule_id,
    (v.value)::bigint AS instance_id,
    'custom' AS rule_type,
    COALESCE(ar.enabled, true) AS enabled,
    ar.rule_name,
    ar.rule_level,
    ar.description,
    ar.metric_name,
    ar.scan_interval_min,
    ar.condition_config,
    ar.recovery_config,
    ar.notification_config,
    now(),
    now()
FROM alert_rule ar
JOIN LATERAL jsonb_array_elements_text(
        COALESCE(ar.scope_config->'instanceIds', '[]'::jsonb)
     ) AS v(value) ON true
WHERE ar.rule_type = 'custom';

-- 删除旧自定义规则模板记录
DELETE FROM alert_rule
WHERE rule_type = 'custom';

-- alert_rule 仅保留内置模板，去除作用域/类型字段
ALTER TABLE alert_rule
    DROP COLUMN IF EXISTS scope_type,
    DROP COLUMN IF EXISTS scope_config,
    DROP COLUMN IF EXISTS rule_type;
