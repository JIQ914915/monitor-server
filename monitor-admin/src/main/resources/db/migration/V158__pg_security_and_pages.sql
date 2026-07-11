-- =============================================================
-- V158：PostgreSQL 支持（二期收官）——安全巡检采集 + E 段页面菜单
--   采集侧新增（monitor-collector-postgresql）：
--     pg_security  天级：SSL 连接占比（pg_stat_ssl）+ 角色审计（pg_roles / pg_authid 尽力而为）
--   前端新增页面（monitor_pg 分组下）：
--     性能分析 monitor/pg/performance   历史趋势：连接/吞吐/等待事件/检查点/WAL/IO/膨胀回卷/容量
--     复制监控 monitor/pg/replication   角色/逐从库三段延迟/复制槽/归档
--     容量与对象 monitor/pg/objects     表热点/膨胀 Top/未使用索引/失效索引
-- 注：全脚本幂等。
-- =============================================================

-- ---- 1. 指标定义 ----
INSERT INTO metric_definition (metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description) VALUES
('pg.security.ssl_conn_pct',      'SSL 连接占比',       'postgresql', 'security', 'analysis', 'numeric', 'percent', 'pg.pg_security', 'raw', '1d', '当前客户端连接中走 SSL 加密的百分比（采集时点快照）'),
('pg.security.superuser_count',   '超级用户数',         'postgresql', 'security', 'guard',    'numeric', 'count',   'pg.pg_security', 'raw', '1d', '可登录的超级用户角色数，越少越好；日常运维应使用最小权限角色'),
('pg.security.never_expire_count','密码永不过期角色数', 'postgresql', 'security', 'analysis', 'numeric', 'count',   'pg.pg_security', 'raw', '1d', '可登录且未设置密码有效期（rolvaliduntil）的角色数，含正常场景，仅作巡检提示'),
('pg.security.nopassword_count',  '无密码角色数',       'postgresql', 'security', 'guard',    'numeric', 'count',   'pg.pg_security', 'raw', '1d', '可登录且未设置密码的角色数（读 pg_authid 需超级用户权限，监控账号无权限时不产出）'),
('pg.security.roles_info',        '角色审计明细',       'postgresql', 'security', 'analysis', 'text',    NULL,      'pg.pg_security', 'raw', '1d', '角色审计 JSON：超级用户清单、可登录角色总数、无密码角色清单（无权限时注明未检查）')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 菜单：PostgreSQL 分组 → 性能分析 / 复制监控 / 容量与对象 ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    '性能分析', 'pg_performance', 'menu', '实例级', 'TrendCharts', 'performance', 'monitor/pg/performance',
    'pg_performance:view', 6, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
    'PostgreSQL 历史性能分析：连接/吞吐缓存/临时对象/锁/等待事件/检查点/WAL/IO(16+)/膨胀回卷/容量趋势，阈值线与事件标注'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    '复制监控', 'pg_replication', 'menu', '实例级', 'Connection', 'replication', 'monitor/pg/replication',
    'pg_replication:view', 7, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
    'PostgreSQL 复制监控：角色/回放延迟/逐从库三段延迟明细/复制槽积压/WAL 归档状态'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    '容量与对象', 'pg_objects', 'menu', '实例级', 'Files', 'objects', 'monitor/pg/objects',
    'pg_objects:view', 8, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
    'PostgreSQL 容量与对象分析：表热点 Top/膨胀 Top 表/疑似未使用索引/失效索引'
)
ON CONFLICT (code) DO NOTHING;

-- ---- 3. 预设角色权限 ----
UPDATE sys_role
   SET permissions = permissions || '["pg_performance:view","pg_replication:view","pg_objects:view"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["pg_performance:view","pg_replication:view","pg_objects:view"]'::jsonb);
