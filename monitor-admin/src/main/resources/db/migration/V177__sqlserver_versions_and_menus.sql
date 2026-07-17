-- SQL Server 接入补全：修复版本种子并登记可实际访问的实例监控菜单。
INSERT INTO database_version (db_type, version_code, version_name, sort_order, description)
VALUES
('sqlserver', '2017', 'SQL Server 2017', 1, '扩展支持至 2027-10-12'),
('sqlserver', '2019', 'SQL Server 2019', 2, '扩展支持至 2030-01-08'),
('sqlserver', '2022', 'SQL Server 2022', 3, '主流支持至 2028-01-11'),
('sqlserver', '2025', 'SQL Server 2025', 4, '主流支持至 2031-01-06')
ON CONFLICT (db_type, version_code) DO UPDATE
SET version_name = EXCLUDED.version_name,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES ('SQL Server', 'monitor_sqlserver', 'group', '实例级', 'DataLine', 'sqlserver', NULL, NULL, 3,
        'enabled', TRUE, (SELECT id FROM sys_menu WHERE code = 'monitor'),
        'SQL Server 实例监控：运行状态、告警、事件分析、慢 SQL 与诊断场景')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name, route = EXCLUDED.route, status = 'enabled', visible = TRUE,
    parent_id = EXCLUDED.parent_id, description = EXCLUDED.description, updated_at = NOW();

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT m.name, 'sqlserver_realtime', m.menu_type, m.type, m.icon, m.route,
       'monitor/sqlserver/realtime', m.perm, 1, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_sqlserver'),
       'SQL Server 运行状态、连接、阻塞、内存、I/O 与事务日志关键结论', m.buttons
  FROM sys_menu m WHERE m.code = 'realtime'
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT m.name, 'sqlserver_alert', m.menu_type, m.type, m.icon, m.route,
       'monitor/shared/alert', m.perm, 2, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_sqlserver'),
       'SQL Server 告警规则、事件状态、原因与人工处置建议', m.buttons
  FROM sys_menu m WHERE m.code = 'alert'
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT m.name, 'sqlserver_slowsql', m.menu_type, m.type, m.icon, m.route,
       'monitor/shared/slowsql', m.perm, 3, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_sqlserver'),
       'SQL Server Query Store 与 DMV Top SQL 分析；能力不足时明确显示降级原因', m.buttons
  FROM sys_menu m WHERE m.code = 'slowsql'
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT m.name, 'sqlserver_scenario', m.menu_type, m.type, m.icon, m.route,
       'monitor/shared/scenario', m.perm, 4, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_sqlserver'),
       'SQL Server 多指标场景诊断与排查建议', m.buttons
  FROM sys_menu m WHERE m.code = 'scenario_mgmt'
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, active_menu, description, buttons)
SELECT m.name, 'sqlserver_alert_drilldown', m.menu_type, m.type, m.icon, m.route,
       'monitor/shared/alert/drilldown', m.perm, 99, 'enabled', FALSE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_sqlserver'), '/monitor/sqlserver/alert',
       'SQL Server 告警事件指标证据、可能原因、排查路径和人工处置建议', m.buttons
  FROM sys_menu m WHERE m.code = 'alert_drilldown'
ON CONFLICT (code) DO NOTHING;
