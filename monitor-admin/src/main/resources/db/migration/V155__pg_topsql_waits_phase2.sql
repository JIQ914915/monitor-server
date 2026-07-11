-- =============================================================
-- V155：PostgreSQL 支持（二期 B）——Top SQL / 慢SQL样本 / 等待事件 / 扩展探测
--   采集侧新增（monitor-collector-postgresql）：
--     pg_top_sql          小时级：pg_stat_statements 差值 → metric_top_sql（复用 MySQL 表与页面）
--     pg_slow_sql_sample  分钟级：pg_stat_activity 运行中采样 → metric_slow_sql_sample
--     pg_wait_events      分钟级：pg_stat_activity 等待事件采样 → pg.waits.* + top_events 文本
--     pg_extensions       天级：pg_stat_statements / pgaudit 扩展状态探测 → pg.ext.*
--   本脚本落：指标定义 + PG 分组「慢SQL分析」菜单。
-- 注：全脚本幂等。
-- =============================================================

-- ---- 1. 指标定义 ----
INSERT INTO metric_definition (metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description) VALUES
-- 等待事件（分钟采样：当前时刻各大类等待中的后端数）
('pg.waits.lock_count',    '等待中会话数-重量级锁', 'postgresql', 'performance', 'guard',    'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '采样时刻处于 Lock 等待（行/表锁）的后端数，持续大于 0 说明存在锁竞争'),
('pg.waits.lwlock_count',  '等待中会话数-轻量级锁', 'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '采样时刻处于 LWLock 等待（共享内存结构竞争）的后端数'),
('pg.waits.io_count',      '等待中会话数-IO',       'postgresql', 'performance', 'guard',    'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '采样时刻处于数据文件/WAL 读写等待的后端数，持续偏高指向磁盘瓶颈'),
('pg.waits.ipc_count',     '等待中会话数-IPC',      'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '采样时刻处于进程间通信等待（并行查询/复制同步）的后端数'),
('pg.waits.client_count',  '等待中会话数-客户端',   'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '采样时刻等待客户端收发数据的后端数，偏高指向网络慢或应用取数慢'),
('pg.waits.timeout_count', '等待中会话数-定时',     'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '采样时刻处于定时等待的后端数'),
('pg.waits.other_count',   '等待中会话数-其他',     'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_wait_events', 'raw', '1m', '其余等待大类（Extension/BufferPin 等）的后端数'),
('pg.waits.top_events',    'Top 等待事件明细',      'postgresql', 'performance', 'analysis', 'text',    NULL,    'pg.pg_wait_events', 'raw', '1m', '采样时刻 Top10 具体等待事件 JSON：[{"event":"Lock:transactionid","count":3}]'),
-- 扩展探测（天级）
('pg.ext.pg_stat_statements', 'pg_stat_statements 状态', 'postgresql', 'availability', 'analysis', 'numeric', 'count', 'pg.pg_extensions', 'raw', '1d', '0=未启用；1=差一步（只加载或只创建）；2=就绪。就绪后 Top SQL 指纹分析自动开始采集'),
('pg.ext.pgaudit',            'pgaudit 状态',            'postgresql', 'security',     'analysis', 'numeric', 'count', 'pg.pg_extensions', 'raw', '1d', '0=未启用；1=差一步；2=就绪。审计对接为后续阶段，先探测状态')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. PG 分组「慢SQL分析」菜单（复用通用慢SQL页，包装组件挂载） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT m.name, 'pg_slowsql', 'menu', '实例级', m.icon, m.route, 'monitor/pg/slowsql',
       m.perm, 4, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
       'PostgreSQL 慢SQL/Top SQL 分析（数据来自 pg_stat_statements 差值与 pg_stat_activity 采样，与 MySQL 共用通用页）', m.buttons
  FROM sys_menu m
 WHERE m.code = 'slowsql'
ON CONFLICT (code) DO NOTHING;
