-- =============================================================
-- V157：PostgreSQL 支持（二期 C8/C9/D2/D3 收尾）
--   采集侧新增（monitor-collector-postgresql）：
--     pg_table_stat   小时级：表热点差值 Top N（pg_stat_user_tables）
--                     天级：疑似未使用索引 + 失效索引（pg_stat_user_indexes / pg_index）
--     pg_repl_detail  分钟级：逐从库三段延迟（pg_stat_replication，对象级）
--     pg_stat_io      分钟级：实例级 I/O 速率（PG 16+ pg_stat_io，差值）
--   服务侧：
--     参数体检 PG 版（ParamAdviceService 按类型分派，9 条规则）
--     params/current、unused-index/page 按类型分派
-- 注：全脚本幂等。
-- =============================================================

-- ---- 1. 指标定义 ----
INSERT INTO metric_definition (metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description) VALUES
-- 表热点（小时级对象指标，object_type=table）
('pgtable.seq_scan',      '表顺序扫描次数',   'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_table_stat', 'delta', '1h', '本周期该表顺序扫描（全表扫描）次数，大表上持续偏高说明缺索引'),
('pgtable.idx_scan',      '表索引扫描次数',   'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_table_stat', 'delta', '1h', '本周期该表索引扫描次数'),
('pgtable.read_rows',     '表读取行数',       'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_table_stat', 'delta', '1h', '本周期该表读取行数（顺扫行 + 索引取回行），衡量表读热度'),
('pgtable.write_rows',    '表写入行数',       'postgresql', 'performance', 'analysis', 'numeric', 'count', 'pg.pg_table_stat', 'delta', '1h', '本周期该表写入行数（insert+update+delete），衡量表写热度'),
-- 索引分析（天级文本）
('pg.index.unused_list',  '疑似未使用索引清单', 'postgresql', 'performance', 'analysis', 'text', NULL, 'pg.pg_table_stat', 'raw', '1d', 'idx_scan=0 的非主键/非唯一索引 JSON 清单（含 uptimeDays，运行时间短时结论不可靠）'),
('pg.index.invalid_list', '失效索引清单',     'postgresql', 'availability', 'guard',   'text', NULL, 'pg.pg_table_stat', 'raw', '1d', 'indisvalid=false 的失效索引（多为 CREATE INDEX CONCURRENTLY 失败残留），不参与查询但拖累写入，需重建或删除'),
-- 逐从库复制细分（分钟级对象指标，object_type=replica）
('pgrepl.lag_bytes',      '从库落后字节数',   'postgresql', 'replication', 'guard',    'numeric', 'bytes', 'pg.pg_repl_detail', 'raw', '1m', '主库当前 WAL 位点与该从库回放位点的字节差（总落后量）'),
('pgrepl.write_lag_ms',   '从库网络传输延迟', 'postgresql', 'replication', 'analysis', 'numeric', 'ms',    'pg.pg_repl_detail', 'raw', '1m', '三段延迟之一：WAL 发出到从库写入的耗时，偏高说明网络慢'),
('pgrepl.flush_lag_ms',   '从库刷盘延迟',     'postgresql', 'replication', 'analysis', 'numeric', 'ms',    'pg.pg_repl_detail', 'raw', '1m', '三段延迟之二：从库写入到刷盘的耗时，偏高说明从库磁盘慢'),
('pgrepl.replay_lag_ms',  '从库回放延迟',     'postgresql', 'replication', 'analysis', 'numeric', 'ms',    'pg.pg_repl_detail', 'raw', '1m', '三段延迟之三：从库刷盘到回放完成的耗时，偏高说明回放跟不上（单进程回放/锁冲突/从库查询冲突）'),
-- 实例级 I/O（分钟级，PG 16+）
('pg.io.read_rate',           '块读速率',     'postgresql', 'performance', 'analysis', 'numeric', 'qps',   'pg.pg_stat_io', 'delta', '1m', '每秒块读操作数（pg_stat_io 关系对象汇总，PG 16+）'),
('pg.io.write_rate',          '块写速率',     'postgresql', 'performance', 'analysis', 'numeric', 'qps',   'pg.pg_stat_io', 'delta', '1m', '每秒块写操作数（PG 16+）'),
('pg.io.extend_rate',         '文件扩展速率', 'postgresql', 'performance', 'analysis', 'numeric', 'qps',   'pg.pg_stat_io', 'delta', '1m', '每秒文件扩展操作数，持续偏高说明大量数据写入（PG 16+）'),
('pg.io.read_time_ms_delta',  '块读耗时',     'postgresql', 'performance', 'analysis', 'numeric', 'ms',    'pg.pg_stat_io', 'delta', '1m', '周期内块读耗时毫秒（需 track_io_timing=on，未开启恒为 0）'),
('pg.io.write_time_ms_delta', '块写耗时',     'postgresql', 'performance', 'analysis', 'numeric', 'ms',    'pg.pg_stat_io', 'delta', '1m', '周期内块写耗时毫秒（需 track_io_timing=on，未开启恒为 0）')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 菜单：PostgreSQL 分组 → 配置巡检 ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    '配置巡检', 'pg_config', 'menu', '实例级', 'SetUp', 'config', 'monitor/pg/config',
    'pg_config:view', 5, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
    'PostgreSQL 配置巡检：关键参数快照 + 规则化参数体检（配置值与运行指标联合判断，只出建议不出手）'
)
ON CONFLICT (code) DO NOTHING;

-- ---- 3. 预设角色权限 ----
UPDATE sys_role
   SET permissions = permissions || '["pg_config:view"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["pg_config:view"]'::jsonb);
