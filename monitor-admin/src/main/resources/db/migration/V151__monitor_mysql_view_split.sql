-- =============================================================
-- V151：监控视图按数据库类型拆分（MySQL）
--
--   views/monitor/* → views/monitor/mysql/*
--   路由：/monitor/{page} → /monitor/mysql/{page}
--   组件：monitor/{page} → monitor/mysql/{page}
--
--   在「监控视图」下新增 mysql 分组，原实例级监控菜单挂到该分组下，
--   为后续 oracle / sqlserver 等同级扩展预留结构。
-- =============================================================

-- ---- 1. MySQL 分组（挂在 monitor 下）----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    'MySQL', 'monitor_mysql', 'group', '实例级', 'Coin', 'mysql', NULL, NULL, 1, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor'),
    'MySQL 实例监控分组：实时概况、告警、慢 SQL、性能、场景及二期分析页'
)
ON CONFLICT (code) DO NOTHING;

-- ---- 2. 原挂在 monitor 下的子节点（含分组与页面）改挂到 monitor_mysql ----
UPDATE sys_menu
   SET parent_id = (SELECT id FROM sys_menu WHERE code = 'monitor_mysql'),
       updated_at = NOW()
 WHERE parent_id = (SELECT id FROM sys_menu WHERE code = 'monitor')
   AND code <> 'monitor_mysql';

-- ---- 3. 组件路径：monitor/xxx → monitor/mysql/xxx ----
UPDATE sys_menu
   SET component = 'monitor/mysql/' || substring(component FROM length('monitor/') + 1),
       updated_at = NOW()
 WHERE component LIKE 'monitor/%'
   AND component NOT LIKE 'monitor/mysql/%';

-- ---- 4. 告警下钻高亮归属：/monitor/alert → /monitor/mysql/alert ----
UPDATE sys_menu
   SET active_menu = '/monitor/mysql/alert',
       updated_at = NOW()
 WHERE code = 'alert_drilldown'
   AND (active_menu IS NULL OR active_menu = '/monitor/alert');

-- 兜底：其它仍指向旧 /monitor/ 高亮路径的菜单一并迁移
UPDATE sys_menu
   SET active_menu = '/monitor/mysql/' || substring(active_menu FROM length('/monitor/') + 1),
       updated_at = NOW()
 WHERE active_menu LIKE '/monitor/%'
   AND active_menu NOT LIKE '/monitor/mysql/%';
