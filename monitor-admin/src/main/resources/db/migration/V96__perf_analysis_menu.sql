-- =============================================================
-- 性能分析菜单：归属「监控视图」分组，排在实时概况之后
--   路由 /monitor/performance，组件 monitor/performance
--   页面能力：分类趋势图（连接/吞吐/SQL/InnoDB/事务锁/复制/容量）+ 时间范围切换 + 数据导出
--   小时级数据由 1m 连续聚合降采样（metric_data_1h_cagg），不做小时级重复采集
--   权限码：perf_analysis:view（查看）/ perf_analysis:export（导出）
-- =============================================================

-- 为性能分析腾出 sort=3：监控视图分组内后续菜单依次后移
UPDATE sys_menu SET sort = sort + 1
 WHERE parent_id = (SELECT id FROM sys_menu WHERE code = 'monitor')
   AND code IN ('alert', 'slowsql', 'report');

-- 菜单行
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('性能分析', 'perf_analysis', 'menu', '实例级', 'DataAnalysis',
        'performance', 'monitor/performance',
        'perf_analysis:view', 3, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'monitor'),
        '历史性能分析：分类指标趋势图（小时级降采样视图），支持时间范围切换与数据导出',
        '[
          {"name": "导出数据", "code": "perf_analysis:export", "status": "enabled"}
        ]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ── 角色权限码同步 ─────────────────────────────────────────────────
-- super_admin：已拥有通配 *:*，无需单独添加

-- dba / ops：查看 + 导出
UPDATE sys_role
   SET permissions = permissions
       || '["perf_analysis:view", "perf_analysis:export"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["perf_analysis:view"]'::jsonb);

-- auditor：仅查看
UPDATE sys_role
   SET permissions = permissions
       || '["perf_analysis:view"]'::jsonb
 WHERE code = 'auditor'
   AND NOT (permissions @> '["perf_analysis:view"]'::jsonb);
