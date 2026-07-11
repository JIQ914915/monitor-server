-- =============================================================
-- V144：实例动态菜单二期扩展分组（§11.11.3 / §20.2 扩展预留分组落地）
--
--   监控视图（monitor）分组下新增 4 个子分组 + 6 个页面：
--     连接与会话 (connection_session) ─ 连接分析 (monitor/session)
--     锁与事务   (lock_transaction)   ─ 锁等待分析 (monitor/lock)
--                                     ─ 死锁分析   (monitor/deadlock)
--                                     ─ 长事务分析 (monitor/transaction)
--     高可用分析 (ha_analysis)        ─ 复制监控   (monitor/replication)
--     存储分析   (storage_analysis)   ─ 容量分析   (monitor/storage)
--
--   页面复用既有采集数据与组件（连接 Tab / 资源 Tab / 趋势图 / 阻塞快照等），
--   版本不适配时页面内自行降级提示（applicableScopes 版本门控暂不实现）。
-- =============================================================

-- ---- 1. 四个子分组（group 节点，无组件）----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES
('连接与会话', 'connection_session', 'group', '实例级', 'Connection', 'session', NULL, NULL, 7, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'monitor'),
 '连接与会话分析分组（二期扩展）：连接来源、状态分布、长连接'),
('锁与事务', 'lock_transaction', 'group', '实例级', 'Lock', 'lock', NULL, NULL, 8, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'monitor'),
 '锁与事务分析分组（二期扩展）：锁等待、死锁、长事务'),
('高可用分析', 'ha_analysis', 'group', '实例级', 'CopyDocument', 'ha', NULL, NULL, 9, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'monitor'),
 '高可用分析分组（二期扩展）：复制监控'),
('存储分析', 'storage_analysis', 'group', '实例级', 'Coin', 'storage', NULL, NULL, 10, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'monitor'),
 '存储分析分组（二期扩展）：容量、表空间、表 I/O 热点')
ON CONFLICT (code) DO NOTHING;

-- ---- 2. 页面菜单 ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES
('连接分析', 'conn_analysis', 'menu', '实例级', 'DataLine', 'analysis', 'monitor/session',
 'conn_analysis:view', 1, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'connection_session'),
 '连接概览、来源 Top、状态分布、长连接与线程状态细化'),
('锁等待分析', 'lock_wait', 'menu', '实例级', 'Warning', 'wait', 'monitor/lock',
 'lock_wait:view', 1, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'lock_transaction'),
 '行锁/表锁等待趋势、被阻塞会话、表级锁明细与等待事件锁大类'),
('死锁分析', 'deadlock_analysis', 'menu', '实例级', 'CircleClose', 'deadlock', 'monitor/deadlock',
 'deadlock_analysis:view', 2, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'lock_transaction'),
 '死锁次数趋势与最近一次死锁现场全文（SHOW ENGINE INNODB STATUS 解析）'),
('长事务分析', 'long_trx', 'menu', '实例级', 'Timer', 'transaction', 'monitor/transaction',
 'long_trx:view', 3, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'lock_transaction'),
 '活跃/最长事务趋势、Undo 历史链长度与长连接明细'),
('复制监控', 'replication_monitor', 'menu', '实例级', 'Refresh', 'replication', 'monitor/replication',
 'replication_monitor:view', 1, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'ha_analysis'),
 '主从复制状态、延迟趋势与 IO/SQL 线程运行状态（从库实例）'),
('容量分析', 'capacity_analysis', 'menu', '实例级', 'PieChart', 'capacity', 'monitor/storage',
 'capacity_analysis:view', 1, 'enabled', TRUE,
 (SELECT id FROM sys_menu WHERE code = 'storage_analysis'),
 '表空间 Top、容量预测、Buffer Pool、表 I/O 热点与疑似未使用索引')
ON CONFLICT (code) DO NOTHING;

-- ---- 3. 角色权限码同步 ----
-- super_admin：*:* 通配，无需单独添加
-- 规格 5.5 权限矩阵：历史性能分析 / Top SQL / 锁与事务对全部角色均为只读（R）
UPDATE sys_role
   SET permissions = permissions
       || '["conn_analysis:view", "lock_wait:view", "deadlock_analysis:view", "long_trx:view", "replication_monitor:view", "capacity_analysis:view"]'::jsonb
 WHERE code IN ('dba', 'ops', 'auditor')
   AND NOT (permissions @> '["conn_analysis:view"]'::jsonb);
