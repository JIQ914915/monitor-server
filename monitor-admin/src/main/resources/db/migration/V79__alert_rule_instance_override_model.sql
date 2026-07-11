-- =============================================================
-- V79：内置规则实例化配置模型
-- 1) alert_rule 仅作为系统内置模板，默认停用
-- 2) 内置规则实例化参数全部落在 alert_rule_instance_config
-- 3) 历史全局改动尽量迁移到实例配置（启用态 + 调频覆盖）
-- =============================================================

-- 扩展实例配置：支持实例级参数覆盖
ALTER TABLE alert_rule_instance_config
    ADD COLUMN IF NOT EXISTS scan_interval_min INT,
    ADD COLUMN IF NOT EXISTS metric_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS condition_config JSONB,
    ADD COLUMN IF NOT EXISTS recovery_config JSONB,
    ADD COLUMN IF NOT EXISTS notification_config JSONB;

-- alert_rule_metric_ref 仅作为模板元数据，不承担启停语义
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'alert_rule_metric_ref'
    ) THEN
        ALTER TABLE alert_rule_metric_ref
            DROP COLUMN IF EXISTS enabled;
    END IF;
END $$;

COMMENT ON COLUMN alert_rule_instance_config.scan_interval_min IS '实例级扫描间隔（分钟），为空继承模板';
COMMENT ON COLUMN alert_rule_instance_config.metric_name IS '实例级指标覆盖（场景内置规则可选），为空继承模板';
COMMENT ON COLUMN alert_rule_instance_config.condition_config IS '实例级触发条件覆盖，jsonb';
COMMENT ON COLUMN alert_rule_instance_config.recovery_config IS '实例级恢复条件覆盖，jsonb';
COMMENT ON COLUMN alert_rule_instance_config.notification_config IS '实例级通知配置覆盖，jsonb';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_rule_inst_cfg_scan_interval_positive'
    ) THEN
        ALTER TABLE alert_rule_instance_config
            ADD CONSTRAINT ck_rule_inst_cfg_scan_interval_positive
            CHECK (scan_interval_min IS NULL OR scan_interval_min >= 1);
    END IF;
END $$;

-- ------------------------------------------------------------------
-- 历史迁移（尽量保留原行为）
-- A. 对“全局启用”的内置规则，为每个适用实例创建启用配置
-- B. 对“全局调频覆盖”的内置规则（scan_interval_source=USER_OVERRIDE），
--    为每个适用实例创建配置并下沉频率
-- C. 对已有实例配置记录，补齐新扩展字段的默认值（按模板填充空值）
-- ------------------------------------------------------------------

WITH target AS (
    SELECT
        ar.id AS rule_id,
        di.id AS instance_id,
        ar.enabled,
        ar.scan_interval_min,
        ar.metric_name,
        ar.condition_config,
        ar.recovery_config,
        ar.notification_config
    FROM alert_rule ar
    JOIN db_instance di
      ON ar.rule_type = 'builtin'
     AND ar.scope_type = 'db_type'
     AND ar.db_type_id = di.db_type_id
     AND (
            ar.db_version_ids IS NULL
         OR jsonb_typeof(ar.db_version_ids) <> 'array'
         OR jsonb_array_length(ar.db_version_ids) = 0
         OR di.db_version_id IS NULL
         OR ar.db_version_ids @> to_jsonb(ARRAY[di.db_version_id]::bigint[])
     )
    WHERE ar.rule_type = 'builtin'
      AND (
           ar.enabled = TRUE
           OR COALESCE(ar.scan_interval_source, '') = 'USER_OVERRIDE'
      )
)
INSERT INTO alert_rule_instance_config (
    rule_id, instance_id, enabled, scan_interval_min, metric_name,
    condition_config, recovery_config, notification_config, created_at, updated_at
)
SELECT
    t.rule_id,
    t.instance_id,
    TRUE,
    t.scan_interval_min,
    t.metric_name,
    t.condition_config,
    t.recovery_config,
    t.notification_config,
    now(),
    now()
FROM target t
ON CONFLICT (rule_id, instance_id) DO UPDATE
SET enabled             = EXCLUDED.enabled,
    scan_interval_min   = COALESCE(alert_rule_instance_config.scan_interval_min, EXCLUDED.scan_interval_min),
    metric_name         = COALESCE(alert_rule_instance_config.metric_name, EXCLUDED.metric_name),
    condition_config    = COALESCE(alert_rule_instance_config.condition_config, EXCLUDED.condition_config),
    recovery_config     = COALESCE(alert_rule_instance_config.recovery_config, EXCLUDED.recovery_config),
    notification_config = COALESCE(alert_rule_instance_config.notification_config, EXCLUDED.notification_config),
    updated_at          = now();

UPDATE alert_rule_instance_config cfg
SET scan_interval_min   = COALESCE(cfg.scan_interval_min, ar.scan_interval_min),
    metric_name         = COALESCE(cfg.metric_name, ar.metric_name),
    condition_config    = COALESCE(cfg.condition_config, ar.condition_config),
    recovery_config     = COALESCE(cfg.recovery_config, ar.recovery_config),
    notification_config = COALESCE(cfg.notification_config, ar.notification_config),
    updated_at          = now()
FROM alert_rule ar
WHERE cfg.rule_id = ar.id
  AND ar.rule_type = 'builtin';

-- 内置规则模板默认停用（实例侧显式启用）
UPDATE alert_rule
SET enabled = FALSE
WHERE rule_type = 'builtin';
