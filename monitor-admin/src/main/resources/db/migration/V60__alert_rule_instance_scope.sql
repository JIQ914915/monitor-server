-- =============================================================
-- V59：告警规则实例级作用域重构
--
-- 核心设计变更：
--   1. alert_rule 表新增 db_type 列，内置规则绑定到数据库类型（如 MySQL），
--      scope_type 从 'all' 改为 'db_type'，评估引擎按 db_type 过滤目标实例。
--   2. 新建 alert_rule_instance_config 表，记录内置规则在某实例上的
--      启用/停用覆盖（不覆盖则继承 alert_rule.enabled 全局默认值）。
--   3. 自定义规则保持 scope_type='instance'，scope_config = {"instanceIds":[id]} 不变。
-- =============================================================

-- ---- 1. alert_rule 新增 db_type 列 ----
ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS db_type VARCHAR(50);

COMMENT ON COLUMN alert_rule.db_type IS '适用数据库类型（如 MySQL / Oracle / PostgreSQL），内置规则必填，自定义规则可空';

-- ---- 2. 更新现有内置规则：scope_type → db_type，db_type → MySQL ----
UPDATE alert_rule
SET db_type    = 'MySQL',
    scope_type = 'db_type'
WHERE rule_type = 'builtin';

-- ---- 3. 新建 alert_rule_instance_config 表 ----
CREATE TABLE IF NOT EXISTS alert_rule_instance_config (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id     BIGINT  NOT NULL REFERENCES alert_rule (id) ON DELETE CASCADE,
    instance_id BIGINT  NOT NULL,
    enabled     BOOLEAN NOT NULL,                  -- 覆盖值（与 alert_rule.enabled 可不同）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_rule_instance UNIQUE (rule_id, instance_id)
);

COMMENT ON TABLE alert_rule_instance_config IS
    '内置告警规则在特定实例上的启用/停用覆盖配置。'
    '存在记录时以本表 enabled 为准，不存在时继承 alert_rule.enabled 全局默认。';

CREATE INDEX IF NOT EXISTS idx_rule_inst_cfg_instance ON alert_rule_instance_config (instance_id);

CREATE TRIGGER trg_alert_rule_inst_cfg_updated_at
    BEFORE UPDATE ON alert_rule_instance_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
