-- =============================================================================
-- V153: PostgreSQL 分组补充隐藏的事件分析（告警下钻）菜单
-- 背景：下钻页路由由 sys_menu 动态生成。PG 告警事件下钻此前只能跳到
--       /monitor/mysql/alert/drilldown，落在被菜单过滤掉的 MySQL 分组，
--       导致菜单与内容区脱节。本迁移在 monitor_pg 分组下挂同构隐藏菜单，
--       前端以包装组件复用 MySQL 下钻页实现。
-- =============================================================================

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT m.name, 'pg_alert_drilldown', m.menu_type, m.type, m.icon, m.route,
       'monitor/pg/alert/drilldown', m.perm, m.sort, m.status, FALSE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
       'PostgreSQL 告警事件下钻分析（复用通用事件分析页，菜单隐藏，仅作路由挂载）', m.buttons
  FROM sys_menu m
 WHERE m.code = 'alert_drilldown'
ON CONFLICT (code) DO NOTHING;
