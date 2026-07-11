-- =============================================================
-- 采集器管理模块菜单入库：
--   路由 /system/collector，组件 system/collector/index
--   页面只读（仅查看采集日志），无按钮权限点
--   权限码 collector_mgmt（DBA/运维可见，管理员通配 *:*）
-- =============================================================

-- 菜单行
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('采集器管理', 'collector_mgmt', 'menu', '系统级', 'DataLine',
        'collector', 'system/collector/index',
        'collector_mgmt', 17, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'),
        '采集任务运行状态与采集日志查看',
        '[]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- 移除旧权限码（V2 种子数据遗留的 collector:view / collector:edit），新增 collector_mgmt
UPDATE sys_role
   SET permissions = (
       (permissions::jsonb - 'collector:view' - 'collector:edit')
       || '["collector_mgmt"]'::jsonb
   )
 WHERE code IN ('dba', 'ops');
