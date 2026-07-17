-- SQL Server 历史性能分析：复用通用趋势接口与告警事件标记，不增加高风险自动处置。
UPDATE sys_menu
   SET sort = CASE code
                  WHEN 'sqlserver_alert' THEN 3
                  WHEN 'sqlserver_slowsql' THEN 4
                  WHEN 'sqlserver_scenario' THEN 5
                  ELSE sort
              END,
       updated_at = NOW()
 WHERE code IN ('sqlserver_alert', 'sqlserver_slowsql', 'sqlserver_scenario');

INSERT INTO sys_menu
    (name, code, menu_type, type, icon, route, component, perm, sort,
     status, visible, parent_id, description, buttons)
VALUES
    ('性能分析', 'sqlserver_performance', 'menu', '实例级', 'TrendCharts',
     'performance', 'monitor/sqlserver/performance', 'sqlserver_performance:view', 2,
     'enabled', TRUE, (SELECT id FROM sys_menu WHERE code = 'monitor_sqlserver'),
     'SQL Server 历史性能分析：吞吐、CPU 调度、连接、内存、等待、I/O、阻塞、TempDB 与存储趋势，支持告警事件标记和数据导出',
     '[{"name":"导出数据","code":"sqlserver_performance:export","status":"enabled"}]'::jsonb)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    menu_type = EXCLUDED.menu_type,
    type = EXCLUDED.type,
    icon = EXCLUDED.icon,
    route = EXCLUDED.route,
    component = EXCLUDED.component,
    perm = EXCLUDED.perm,
    sort = EXCLUDED.sort,
    status = EXCLUDED.status,
    visible = EXCLUDED.visible,
    parent_id = EXCLUDED.parent_id,
    description = EXCLUDED.description,
    buttons = EXCLUDED.buttons,
    updated_at = NOW();

-- dba / ops：查看 + 导出；auditor：仅查看。
UPDATE sys_role
   SET permissions = permissions || '["sqlserver_performance:view"]'::jsonb
 WHERE code IN ('dba', 'ops', 'auditor')
   AND NOT (permissions @> '["sqlserver_performance:view"]'::jsonb);

UPDATE sys_role
   SET permissions = permissions || '["sqlserver_performance:export"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["sqlserver_performance:export"]'::jsonb);
