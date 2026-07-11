-- =============================================================
-- 告警事件下钻分析页（§11.7 事件下钻与报告）：隐藏路由菜单。
--   路由 /monitor/alert/drilldown（?eventId= 查询参数携带事件），
--   组件 monitor/alert/drilldown，不在侧边/顶栏菜单显示，
--   高亮归属告警管理菜单（active_menu=/monitor/alert）。
--   权限沿用告警管理（alert_rule:view），不新增权限码与角色变更。
-- =============================================================

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, active_menu, parent_id, description)
VALUES ('事件下钻分析', 'alert_drilldown', 'menu', '实例级', NULL,
        'alert/drilldown', 'monitor/alert/drilldown',
        NULL, 99, 'enabled', FALSE, '/monitor/alert',
        (SELECT id FROM sys_menu WHERE code = 'monitor'),
        '告警事件下钻分析（隐藏路由）：触发信息、关联指标趋势、可能原因、排查路径、建议动作、历史同期对比、处理记录')
ON CONFLICT (code) DO NOTHING;
