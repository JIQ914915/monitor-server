-- =============================================================
-- 内置规则管理（系统设置全局模块）
--   1. 规则数据来源字典：metric（产品库指标）/ target_sql（目标库 SQL）
--   2. 系统设置新增「内置规则管理」菜单 + 按钮权限
--   3. 预设角色权限回填
-- =============================================================

-- 1. 规则数据来源字典
INSERT INTO sys_dict_type (dict_type, dict_name, type, remark) VALUES
  ('alert_rule_data_source', '告警规则数据来源', 'system', '规则评估数据来源：产品库指标 / 目标库 SQL')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort) VALUES
  ('alert_rule_data_source', 'metric',     '产品库指标', 'primary', 1),
  ('alert_rule_data_source', 'target_sql', '目标库 SQL', 'warning', 2)
ON CONFLICT DO NOTHING;

-- 2. 菜单：系统设置 → 内置规则管理
INSERT INTO sys_menu (
    name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description
)
VALUES (
    '内置规则管理', 'system_builtin_rule', 'menu', '系统级',
    'Bell', 'builtin-rule', 'system/builtin-rule',
    'builtin_rule:list', 16, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'system'),
    '内置告警规则模板全局维护：适用类型/版本、阈值默认值、目标库 SQL 评估'
)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_menu
   SET buttons = '[
     {"name":"新增","code":"builtin_rule:create","status":"enabled"},
     {"name":"编辑","code":"builtin_rule:update","status":"enabled"},
     {"name":"删除","code":"builtin_rule:delete","status":"enabled"}
   ]'::jsonb
 WHERE code = 'system_builtin_rule';

-- 3. 预设角色权限回填
UPDATE sys_role
   SET permissions = permissions
       || '["builtin_rule:list","builtin_rule:create","builtin_rule:update","builtin_rule:delete"]'::jsonb
 WHERE code = 'superadmin'
   AND NOT (permissions @> '["builtin_rule:list"]'::jsonb);

UPDATE sys_role
   SET permissions = permissions || '["builtin_rule:list"]'::jsonb
 WHERE code = 'admin'
   AND NOT (permissions @> '["builtin_rule:list"]'::jsonb);
