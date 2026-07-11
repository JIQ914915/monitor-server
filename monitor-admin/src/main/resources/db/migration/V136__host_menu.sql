-- =============================================================
-- V136：主机指标监控（四）——主机管理菜单与权限
-- =============================================================

-- 菜单：系统设置 → 主机管理
INSERT INTO sys_menu (
    name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description
)
VALUES (
    '主机管理', 'system_host', 'menu', '系统级',
    'Monitor', 'host', 'system/host',
    'host:list', 17, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'system'),
    '数据库主机登记与主机指标采集配置（node_exporter 拉取）'
)
ON CONFLICT (code) DO NOTHING;

UPDATE sys_menu
   SET buttons = '[
     {"name":"新增","code":"host:add","status":"enabled"},
     {"name":"编辑","code":"host:edit","status":"enabled"},
     {"name":"删除","code":"host:delete","status":"enabled"},
     {"name":"连通性测试","code":"host:test","status":"enabled"}
   ]'::jsonb
 WHERE code = 'system_host';

-- 预设角色权限回填
UPDATE sys_role
   SET permissions = permissions
       || '["host:list","host:add","host:edit","host:delete","host:test"]'::jsonb
 WHERE code = 'superadmin'
   AND NOT (permissions @> '["host:list"]'::jsonb);

UPDATE sys_role
   SET permissions = permissions || '["host:list","host:test"]'::jsonb
 WHERE code = 'admin'
   AND NOT (permissions @> '["host:list"]'::jsonb);
