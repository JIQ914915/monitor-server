-- =============================================================
-- 菜单完全表驱动：层级 / 路由 / 组件 / 显隐 / 高亮 全部入表
--   menu_type: group(目录/分组) | menu(页面)；按钮权限仍以 buttons(jsonb) 挂在页面行上
--   route:     作为前端路由 path 段（相对父节点；顶级相对 Layout '/')
--   component: 相对 src/views 的组件路径（不含 .vue），目录/分组为空
--   visible:   是否在侧边菜单显示（详情/编辑等页面置 false）
--   active_menu: 隐藏页面的高亮归属菜单 path
-- =============================================================

ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS parent_id   BIGINT;
ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS menu_type   VARCHAR(16)  NOT NULL DEFAULT 'menu';
ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS component   VARCHAR(128);
ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS redirect    VARCHAR(128);
ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS visible     BOOLEAN      NOT NULL DEFAULT TRUE;
ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS active_menu VARCHAR(128);

COMMENT ON COLUMN sys_menu.parent_id   IS '上级菜单ID（NULL=顶级），层级由此表达';
COMMENT ON COLUMN sys_menu.menu_type   IS '节点类型：group 目录/分组、menu 页面';
COMMENT ON COLUMN sys_menu.component   IS '组件路径（相对 src/views，不含 .vue），目录为空';
COMMENT ON COLUMN sys_menu.redirect    IS '重定向目标 path（可空）';
COMMENT ON COLUMN sys_menu.visible     IS '是否在侧边菜单显示（隐藏页面置 false）';
COMMENT ON COLUMN sys_menu.active_menu IS '隐藏页面的高亮归属菜单 path';
COMMENT ON COLUMN sys_menu.route       IS '前端路由 path 段（相对父节点）';

-- ---- 回填既有页面行：route 改为相对段、补 component ----
UPDATE sys_menu SET menu_type='menu', route='instances', component='instance/list'   WHERE code='instances';
UPDATE sys_menu SET menu_type='menu', route='realtime',  component='monitor/realtime' WHERE code='realtime';
UPDATE sys_menu SET menu_type='menu', route='alert',     component='monitor/alert'    WHERE code='alert';
UPDATE sys_menu SET menu_type='menu', route='slowsql',   component='monitor/slowsql'  WHERE code='slowsql';
UPDATE sys_menu SET menu_type='menu', route='report',    component='monitor/report'   WHERE code='report';
UPDATE sys_menu SET menu_type='menu', route='user',      component='system/user'      WHERE code='system_user';
UPDATE sys_menu SET menu_type='menu', route='role',      component='system/role'      WHERE code='system_role';
UPDATE sys_menu SET menu_type='menu', route='group',     component='system/group'     WHERE code='system_group';
UPDATE sys_menu SET menu_type='menu', route='menu',      component='system/menu'      WHERE code='system_menu';
UPDATE sys_menu SET menu_type='menu', route='retention', component='system/retention' WHERE code='data_retention';
UPDATE sys_menu SET menu_type='menu', route='log',       component='system/log'       WHERE code='audit_log';

-- ---- 目录/分组行（原先硬编码在 /auth/menus 中的分组层级） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, perm, sort, status, description)
VALUES ('监控视图', 'monitor', 'group', '实例级', 'TrendCharts', 'monitor', NULL, 2,  'enabled', '监控视图分组')
ON CONFLICT (code) DO NOTHING;
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, perm, sort, status, description)
VALUES ('系统设置', 'system',  'group', '系统级', 'Setting',     'system',  NULL, 10, 'enabled', '系统设置分组')
ON CONFLICT (code) DO NOTHING;

-- ---- 隐藏详情路由（不在菜单显示，高亮归属实例管理） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, active_menu, description)
VALUES ('实例详情', 'instance_detail', 'menu', '系统级', NULL, 'instances/detail/:id', 'instance/detail',
        NULL, 99, 'enabled', FALSE, '/instances', '实例详情（隐藏路由）')
ON CONFLICT (code) DO NOTHING;

-- ---- 挂载父子关系 ----
UPDATE sys_menu SET parent_id = (SELECT id FROM sys_menu WHERE code='monitor')
  WHERE code IN ('realtime', 'alert', 'slowsql', 'report');
UPDATE sys_menu SET parent_id = (SELECT id FROM sys_menu WHERE code='system')
  WHERE code IN ('system_user', 'system_role', 'system_group', 'system_menu', 'data_retention', 'audit_log');
