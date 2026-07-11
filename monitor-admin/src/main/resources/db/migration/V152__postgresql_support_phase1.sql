-- =============================================================
-- V152：PostgreSQL 监控一期（基础监控闭环）
--   1. database_type 登记 POSTGRESQL（driver/url_template/default_port）
--   2. database_version 登记 13/14/15/16
--   3. metric_definition 种子 pg.* 指标（分钟/小时/天级）
--   4. 内置告警规则 builtin.pg.*（db_type_id 指向 POSTGRESQL）
--   5. 实例级菜单：监控视图 → PostgreSQL 分组 + 实时概况页
--   采集实现见 monitor-collector-postgresql 模块（PostgreSqlCollector）。
-- =============================================================

-- ---- 1. 数据库类型 ----
INSERT INTO database_type (code, label, collector_class, driver_class, url_template,
                           default_port, sort_order, description, enabled)
VALUES ('POSTGRESQL', 'PostgreSQL',
        'com.lzzh.monitor.collector.postgresql.PostgreSqlCollector',
        'org.postgresql.Driver',
        'jdbc:postgresql://{host}:{port}/{database}',
        5432, 2,
        'PostgreSQL 13+。注册实例时必须填写监控库名（通常为 postgres 或业务库）；'
        '监控账号需 pg_monitor 角色', TRUE)
ON CONFLICT (code) DO NOTHING;

-- 历史环境可能已有停用的占位行：补全元数据并启用
UPDATE database_type
   SET collector_class = 'com.lzzh.monitor.collector.postgresql.PostgreSqlCollector',
       driver_class = 'org.postgresql.Driver',
       url_template = 'jdbc:postgresql://{host}:{port}/{database}',
       default_port = 5432,
       enabled = TRUE
 WHERE code = 'POSTGRESQL' AND driver_class IS NULL;

-- ---- 2. 版本 ----
INSERT INTO database_version (db_type, version_code, version_name, sort_order, description) VALUES
('postgresql', '13', 'PostgreSQL 13', 1, '社区支持至 2025-11'),
('postgresql', '14', 'PostgreSQL 14', 2, '社区支持至 2026-11'),
('postgresql', '15', 'PostgreSQL 15', 3, '社区支持至 2027-11'),
('postgresql', '16', 'PostgreSQL 16', 4, '社区支持至 2028-11')
ON CONFLICT (db_type, version_code) DO NOTHING;

-- ---- 3. 指标定义 ----
INSERT INTO metric_definition (metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description) VALUES
-- 可用性
('pg.availability',            '实例可达性',       'postgresql', 'availability', 'guard',    'numeric', 'count',   'pg.availability',  'raw',   '1m', '1=实例可连接，0=连接失败（超时/拒绝/认证失败）'),
('pg.uptime',                  '运行时长',         'postgresql', 'availability', 'guard',    'numeric', 'count',   'pg.availability',  'raw',   '1m', 'postmaster 启动至今秒数，突然变小说明实例发生过重启'),
-- 连接域
('pg.conn.total',              '当前连接数',       'postgresql', 'connection',   'guard',    'numeric', 'count',   'pg.connections',   'raw',   '1m', '客户端后端连接总数（pg_stat_activity，不含后台进程）'),
('pg.conn.active',             '活跃连接数',       'postgresql', 'connection',   'guard',    'numeric', 'count',   'pg.connections',   'raw',   '1m', '正在执行语句的连接数（state=active）'),
('pg.conn.idle',               '空闲连接数',       'postgresql', 'connection',   'analysis', 'numeric', 'count',   'pg.connections',   'raw',   '1m', '空闲连接数（state=idle）'),
('pg.conn.idle_in_trx',        '事务中空闲连接数', 'postgresql', 'connection',   'analysis', 'numeric', 'count',   'pg.connections',   'raw',   '1m', '开启事务后闲置的连接数，长期偏高会阻碍 vacuum 并引发表膨胀'),
('pg.conn.waiting',            '等锁连接数',       'postgresql', 'connection',   'guard',    'numeric', 'count',   'pg.connections',   'raw',   '1m', '正在等待锁的连接数（wait_event_type=Lock）'),
('pg.conn.max',                '最大连接数',       'postgresql', 'connection',   'guard',    'numeric', 'count',   'pg.connections',   'raw',   '1m', 'max_connections 参数值'),
('pg.conn.usage',              '连接使用率',       'postgresql', 'connection',   'guard',    'numeric', 'percent', 'pg.connections',   'ratio', '1m', '当前连接数/最大连接数'),
('pg.conn.active_pct',         '活跃连接占比',     'postgresql', 'connection',   'guard',    'numeric', 'percent', 'pg.connections',   'ratio', '1m', '活跃连接数/最大连接数'),
-- 吞吐与缓存
('pg.tps',                     'TPS',              'postgresql', 'traffic',      'guard',    'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒事务数（提交+回滚，pg_stat_database 差值/间隔）'),
('pg.rate.xact_commit',        '事务提交速率',     'postgresql', 'traffic',      'analysis', 'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒提交事务数'),
('pg.rate.xact_rollback',      '事务回滚速率',     'postgresql', 'traffic',      'analysis', 'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒回滚事务数，突增通常意味着应用报错'),
('pg.cache.hit_rate',          '缓存命中率',       'postgresql', 'cache',        'guard',    'numeric', 'percent', 'pg.database_stat', 'ratio', '1m', 'shared_buffers 命中率（blks_hit/(blks_hit+blks_read)），长期低于 90% 建议检查 shared_buffers 配置'),
('pg.rate.tup_fetched',        '行读取速率',       'postgresql', 'traffic',      'analysis', 'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒读取行数'),
('pg.rate.tup_inserted',       '行插入速率',       'postgresql', 'traffic',      'analysis', 'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒插入行数'),
('pg.rate.tup_updated',        '行更新速率',       'postgresql', 'traffic',      'analysis', 'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒更新行数'),
('pg.rate.tup_deleted',        '行删除速率',       'postgresql', 'traffic',      'analysis', 'numeric', 'qps',     'pg.database_stat', 'delta', '1m', '每秒删除行数'),
('pg.delta.temp_files',        '临时文件新增数',   'postgresql', 'traffic',      'analysis', 'numeric', 'count',   'pg.database_stat', 'delta', '1m', '本周期新增落盘临时文件数，持续产生说明 work_mem 不足或存在大排序'),
('pg.rate.temp_bytes',         '临时文件写入速率', 'postgresql', 'traffic',      'analysis', 'numeric', 'bytes',   'pg.database_stat', 'delta', '1m', '每秒写入临时文件字节数'),
('pg.delta.deadlocks',         '死锁新增次数',     'postgresql', 'lock',         'guard',    'numeric', 'count',   'pg.database_stat', 'delta', '1m', '本周期新增死锁次数（pg_stat_database.deadlocks 差值）'),
-- 锁与事务
('pg.locks.waiting',           '等待中锁请求数',   'postgresql', 'lock',         'guard',    'numeric', 'count',   'pg.locks',         'raw',   '1m', '未授予的锁请求数（pg_locks WHERE NOT granted）'),
('pg.blocked_sessions',        '被阻塞会话数',     'postgresql', 'lock',         'guard',    'numeric', 'count',   'pg.locks',         'raw',   '1m', '因等锁被阻塞的客户端会话数'),
('pg.trx.max_seconds',         '最长事务时长',     'postgresql', 'transaction',  'guard',    'numeric', 'count',   'pg.transactions',  'raw',   '1m', '当前最长运行事务的持续秒数，长事务会阻碍 vacuum 并引发表膨胀'),
('pg.trx.active',              '进行中事务数',     'postgresql', 'transaction',  'analysis', 'numeric', 'count',   'pg.transactions',  'raw',   '1m', '当前处于事务中的会话数'),
('pg.trx.idle_in_trx_max_seconds', '最长事务中空闲时长', 'postgresql', 'transaction', 'guard', 'numeric', 'count', 'pg.transactions',  'raw',   '1m', '最长"开事务后闲置"持续秒数，典型原因是应用拿到连接后忘记提交'),
-- 复制域
('pg.repl.is_replica',         '是否从库',         'postgresql', 'replication',  'guard',    'numeric', 'count',   'pg.replication',   'raw',   '1m', '是否处于恢复模式（流复制从库）：1 是 / 0 否'),
('pg.repl.lag_seconds',        '复制回放延迟',     'postgresql', 'replication',  'guard',    'numeric', 'count',   'pg.replication',   'raw',   '1m', '从库最后回放事务距今秒数；注意主库长时间无写入时该值也会自然增长'),
('pg.repl.replica_count',      '下游从库数',       'postgresql', 'replication',  'guard',    'numeric', 'count',   'pg.replication',   'raw',   '1m', '主库视角的流复制从库连接数，减少说明有从库掉线'),
-- 容量域（小时级）
('pg.capacity.db_size_bytes',  '当前库大小',       'postgresql', 'capacity',     'analysis', 'numeric', 'bytes',   'pg.capacity',      'raw',   '1h', '监控连接所在库的磁盘占用'),
('pg.capacity.total_size_bytes','实例总容量',      'postgresql', 'capacity',     'analysis', 'numeric', 'bytes',   'pg.capacity',      'raw',   '1h', '实例内全部业务库总大小（不含模板库）'),
-- 基线（小时级，BaselineDetectJobHandler 产出）
('pg.baseline.tps_deviation_pct',  'TPS 基线偏离',   'postgresql', 'baseline',  'analysis', 'numeric', 'percent', 'baseline.detect',  'trend', '1h', '当前 TPS 相对历史同小时基线的偏离百分比（正=偏高）'),
('pg.baseline.tps_anomaly',        'TPS 基线异常',   'postgresql', 'baseline',  'guard',    'numeric', 'count',   'baseline.detect',  'state', '1h', 'TPS 显著偏离基线标记：1 异常 / 0 正常'),
('pg.baseline.conn_deviation_pct', '连接数基线偏离', 'postgresql', 'baseline',  'analysis', 'numeric', 'percent', 'baseline.detect',  'trend', '1h', '当前连接数相对历史同小时基线的偏离百分比'),
('pg.baseline.conn_anomaly',       '连接数基线异常', 'postgresql', 'baseline',  'guard',    'numeric', 'count',   'baseline.detect',  'state', '1h', '连接数显著偏离基线标记：1 异常 / 0 正常'),
-- 配置域（天级，数值）
('pg.setting.max_connections',           '最大连接数配置',      'postgresql', 'config', 'explain', 'numeric', 'count', 'pg.settings', 'raw', '1d', 'max_connections 参数值'),
('pg.setting.shared_buffers_bytes',      '共享缓冲区大小',      'postgresql', 'config', 'explain', 'numeric', 'bytes', 'pg.settings', 'raw', '1d', 'shared_buffers（字节），一般建议为主机内存的 25% 左右'),
('pg.setting.effective_cache_size_bytes','有效缓存大小',        'postgresql', 'config', 'explain', 'numeric', 'bytes', 'pg.settings', 'raw', '1d', 'effective_cache_size（字节），影响优化器对索引扫描的偏好'),
('pg.setting.work_mem_bytes',            '工作内存',            'postgresql', 'config', 'explain', 'numeric', 'bytes', 'pg.settings', 'raw', '1d', 'work_mem（字节），排序/哈希超出后落盘临时文件'),
('pg.setting.maintenance_work_mem_bytes','维护工作内存',        'postgresql', 'config', 'explain', 'numeric', 'bytes', 'pg.settings', 'raw', '1d', 'maintenance_work_mem（字节），影响 VACUUM/建索引速度'),
('pg.setting.max_wal_size_bytes',        'WAL 最大大小',        'postgresql', 'config', 'explain', 'numeric', 'bytes', 'pg.settings', 'raw', '1d', 'max_wal_size（字节），过小会导致频繁 checkpoint'),
('pg.setting.checkpoint_timeout_seconds','checkpoint 周期',     'postgresql', 'config', 'explain', 'numeric', 'count', 'pg.settings', 'raw', '1d', 'checkpoint_timeout（秒）'),
('pg.setting.autovacuum_max_workers',    'autovacuum 工作进程数','postgresql', 'config', 'explain', 'numeric', 'count', 'pg.settings', 'raw', '1d', 'autovacuum_max_workers 参数值'),
('pg.setting.max_worker_processes',      '最大工作进程数',      'postgresql', 'config', 'explain', 'numeric', 'count', 'pg.settings', 'raw', '1d', 'max_worker_processes 参数值'),
('pg.setting.idle_in_trx_timeout_ms',    '事务中空闲超时',      'postgresql', 'config', 'explain', 'numeric', 'ms',    'pg.settings', 'raw', '1d', 'idle_in_transaction_session_timeout（毫秒），0 表示不限制'),
('pg.setting.statement_timeout_ms',      '语句超时',            'postgresql', 'config', 'explain', 'numeric', 'ms',    'pg.settings', 'raw', '1d', 'statement_timeout（毫秒），0 表示不限制'),
-- 配置域（天级，文本）
('pg.setting_text.wal_level',                'WAL 级别',          'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'wal_level 参数（replica 及以上才支持流复制）'),
('pg.setting_text.archive_mode',             '归档模式',          'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'archive_mode 参数'),
('pg.setting_text.hot_standby',              '热备读',            'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'hot_standby 参数'),
('pg.setting_text.autovacuum',               'autovacuum 开关',   'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'autovacuum 参数，关闭会导致表膨胀与事务 ID 回卷风险'),
('pg.setting_text.ssl',                      'SSL 开关',          'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'ssl 参数'),
('pg.setting_text.shared_preload_libraries', '预加载扩展',        'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'shared_preload_libraries（含 pg_stat_statements 时二期可开启 Top SQL）'),
('pg.setting_text.server_version',           '数据库版本',        'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'server_version 参数文本'),
('pg.setting_text.log_min_duration_statement','慢查询日志阈值',   'postgresql', 'config', 'explain', 'text', NULL, 'pg.settings', 'state', '1d', 'log_min_duration_statement，-1 表示未开启慢查询记录')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 4. 内置告警规则 ----
-- [1] 实例连接失败（level_1，布尔，持续 3 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 实例连接失败',
    'builtin.pg.availability',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.availability',
    '{"operator":"<","threshold":1.0,"duration":180,"conditionType":"boolean",'
    '"displayText":"实例连续 3 分钟无法连接时触发"}',
    '{"operator":">=","threshold":1.0,'
    '"displayText":"实例恢复可连接时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '连续 3 分钟无法建立数据库连接，实例可能宕机、网络中断或认证失败；'
    '请检查 postgres 进程、pg_hba.conf 与主机网络', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [2] 连接使用率过高（level_1，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 连接使用率过高',
    'builtin.pg.conn.usage.critical',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.conn.usage',
    '{"operator":">=","threshold":90.0,"duration":300,"unit":"percent"}',
    '{"operator":"<","threshold":80.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '连接使用率 ≥ 90% 持续 5 分钟，接近 max_connections 上限后新连接将被拒绝；'
    '建议检查连接池配置与事务中空闲连接，必要时调大 max_connections', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [3] 会话被锁阻塞（level_2，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 会话被锁阻塞',
    'builtin.pg.blocked_sessions',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.blocked_sessions',
    '{"operator":">=","threshold":5.0,"duration":300,"unit":"count"}',
    '{"operator":"<","threshold":1.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '≥ 5 个会话因等锁被阻塞持续 5 分钟，可能存在长事务持锁或 DDL 与业务互锁；'
    '建议排查最长事务与其正在执行的语句，由 DBA 评估是否需要人工终止持锁会话', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [4] 长事务（level_2，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 长事务运行',
    'builtin.pg.trx.long',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.trx.max_seconds',
    '{"operator":">=","threshold":1800.0,"unit":"count"}',
    '{"operator":"<","threshold":600.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '存在运行超过 30 分钟的事务。PostgreSQL 中长事务会阻止 VACUUM 回收旧版本行，'
    '引发表膨胀与查询变慢；请确认是否为正常批处理任务', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [5] 事务中空闲过久（level_3，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 事务中空闲过久',
    'builtin.pg.idle_in_trx',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.trx.idle_in_trx_max_seconds',
    '{"operator":">=","threshold":1800.0,"unit":"count"}',
    '{"operator":"<","threshold":300.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '存在开启事务后闲置超过 30 分钟的连接（idle in transaction），通常是应用拿到连接后未提交/回滚；'
    '建议配置 idle_in_transaction_session_timeout 并排查应用连接池', FALSE
) ON CONFLICT (rule_code) DO NOTHING;

-- [6] 复制回放延迟过大（level_2，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 复制延迟过大',
    'builtin.pg.repl.delay.critical',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.repl.lag_seconds',
    '{"operator":">=","threshold":300.0,"duration":300,"unit":"count"}',
    '{"operator":"<","threshold":60.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '从库回放延迟 ≥ 5 分钟且持续 5 分钟。注意：主库长时间无写入时该指标也会自然增长，'
    '请结合主库 TPS 判断；真实延迟通常由从库 IO 瓶颈或大事务回放引起', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [7] 缓存命中率过低（level_3，持续 10 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 缓存命中率过低',
    'builtin.pg.cache.hit_rate.low',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.cache.hit_rate',
    '{"operator":"<","threshold":90.0,"duration":600,"unit":"percent"}',
    '{"operator":">=","threshold":95.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    'shared_buffers 命中率 < 90% 持续 10 分钟，磁盘读显著增多；'
    '建议检查 shared_buffers 配置、是否存在大表全表扫描', FALSE
) ON CONFLICT (rule_code) DO NOTHING;

-- [8] 发生死锁（level_2，布尔，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 发生死锁',
    'builtin.pg.deadlock',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.delta.deadlocks',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"最近一个采集周期内检测到新增死锁时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"后续周期不再新增死锁时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '最近一分钟内发生死锁（PostgreSQL 自动回滚其中一个事务）；'
    '偶发可接受，频繁死锁需梳理业务事务的加锁顺序', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 指标关联 ----
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN (
     'builtin.pg.availability',
     'builtin.pg.conn.usage.critical',
     'builtin.pg.blocked_sessions',
     'builtin.pg.trx.long',
     'builtin.pg.idle_in_trx',
     'builtin.pg.repl.delay.critical',
     'builtin.pg.cache.hit_rate.low',
     'builtin.pg.deadlock'
 )
ON CONFLICT (rule_id, metric_code) DO NOTHING;

-- ---- 5. 菜单：监控视图 → PostgreSQL 分组 + 实时概况 ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    'PostgreSQL', 'monitor_pg', 'group', '实例级', 'Coin', 'pg', NULL, NULL, 2, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor'),
    'PostgreSQL 实例监控分组（一期：实时概况；Top SQL/等待事件等二期扩展）'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description)
VALUES (
    '实时概况', 'pg_realtime', 'menu', '实例级', 'Odometer', 'realtime', 'monitor/pg/realtime',
    'pg_realtime:view', 1, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
    'PostgreSQL 实时概况：可用性、连接、TPS、缓存命中、锁与事务、复制、容量'
)
ON CONFLICT (code) DO NOTHING;

-- 告警管理复用通用告警页（规则列表按实例类型自动下发 builtin.pg.* 规则），权限沿用 alert 菜单
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT '告警管理', 'pg_alert', 'menu', '实例级', m.icon, 'alert', 'monitor/pg/alert',
       m.perm, 2, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
       'PostgreSQL 告警规则与事件管理（与 MySQL 共用通用告警页）', m.buttons
  FROM sys_menu m
 WHERE m.code = 'alert'
ON CONFLICT (code) DO NOTHING;

-- ---- 6. 预设角色权限 ----
UPDATE sys_role
   SET permissions = permissions || '["pg_realtime:view"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["pg_realtime:view"]'::jsonb);
