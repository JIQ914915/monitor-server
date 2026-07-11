-- =============================================================
-- V118：场景实例级阈值调整（§11.8 场景化监控补充）
--   1. scenario_instance_config 增加 condition_overrides：按信号 code 覆盖触发阈值
--   2. 场景管理菜单增加「调整阈值」按钮权限 scenario_mgmt:edit（dba/ops 授予）
-- =============================================================

-- ---- 1. 实例级阈值覆盖 ----
ALTER TABLE scenario_instance_config
    ADD COLUMN IF NOT EXISTS condition_overrides JSONB;
COMMENT ON COLUMN scenario_instance_config.condition_overrides IS
    '实例级阈值覆盖：{信号code: 阈值}，仅覆盖 threshold 数值（运算符/逻辑不可改）；NULL=使用场景模板默认阈值';

-- ---- 2. 菜单按钮：调整阈值 ----
UPDATE sys_menu
   SET buttons = buttons || '[{"name": "调整阈值", "code": "scenario_mgmt:edit", "status": "enabled"}]'::jsonb
 WHERE code = 'scenario_mgmt'
   AND NOT (buttons @> '[{"code": "scenario_mgmt:edit"}]'::jsonb);

-- ---- 3. 预设角色权限：dba / ops 可调整阈值 ----
UPDATE sys_role
   SET permissions = permissions || '["scenario_mgmt:edit"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["scenario_mgmt:edit"]'::jsonb);
