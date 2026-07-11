-- =============================================================
-- V54: 响应时间指标定义 + 配置参数元数据表（P1-2 / P1-3）
-- =============================================================

-- ---- 1. 响应时间指标定义 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.perf.avg_stmt_latency_ms',
 '语句平均延迟',
 'mysql', 'sql', 'analysis', 'numeric', 'ms',
 'mysql.response_time', 'delta', '1m',
 '周期内所有 SQL 语句平均执行延迟（performance_schema.events_statements_summary_global_by_event_name '
 'SUM_TIMER_WAIT / COUNT_STAR 差值，皮秒转毫秒）；需 performance_schema 已启用')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 配置参数元数据表 ----
-- 存储参数描述、分类、是否动态可改、适用版本等，供「配置 Tab」右侧说明列使用。
-- 初始只插入已纳入 VariablesItem 白名单的参数；后续可按需增补。
CREATE TABLE IF NOT EXISTS mysql_param_meta (
    param_name      VARCHAR(128) PRIMARY KEY,   -- 参数名（小写），对应 SHOW VARIABLES 的 Variable_name
    display_name    VARCHAR(256),               -- 友好显示名（中文）
    category        VARCHAR(64),                -- 分类：connection / innodb / logging / security / general
    is_dynamic      BOOLEAN NOT NULL DEFAULT FALSE,  -- 是否运行时动态修改（无需重启）
    unit            VARCHAR(32),                -- 值单位（bytes / seconds / count / bool / …）
    description     TEXT,                       -- 参数说明
    min_version     VARCHAR(16),                -- 适用最低版本（5.6 / 5.7 / 8.0）
    max_version     VARCHAR(16)                 -- 废弃版本（NULL 表示当前版本仍支持）
);
COMMENT ON TABLE mysql_param_meta IS 'MySQL 配置参数元数据：描述、分类、动态性、适用版本（配置 Tab 说明列）';

-- ---- 3. 初始化参数元数据 ----

-- 连接相关
INSERT INTO mysql_param_meta (param_name, display_name, category, is_dynamic, unit, description, min_version)
VALUES
    ('max_connections',     '最大连接数',     'connection', TRUE,  'count',
     '实例允许的最大客户端并发连接数。超出后新连接报 Too many connections 错误。', '5.6'),
    ('wait_timeout',        '非活跃连接超时', 'connection', TRUE,  'seconds',
     '非交互式连接空闲超时秒数，超时后服务器主动关闭连接。', '5.6'),
    ('thread_cache_size',   '线程缓存大小',   'connection', TRUE,  'count',
     '服务器缓存的线程数量，减少频繁创建/销毁线程的开销。', '5.6')
ON CONFLICT (param_name) DO NOTHING;

-- InnoDB
INSERT INTO mysql_param_meta (param_name, display_name, category, is_dynamic, unit, description, min_version)
VALUES
    ('innodb_buffer_pool_size',    'InnoDB 缓冲池大小',     'innodb', TRUE,  'bytes',
     '最重要的 InnoDB 参数，建议设置为物理内存的 50%~80%（专用数据库服务器）。', '5.6'),
    ('innodb_log_file_size',       'Redo Log 文件大小',     'innodb', FALSE, 'bytes',
     '每个 Redo Log 文件大小，值越大崩溃恢复越慢但写入性能更好。', '5.6'),
    ('innodb_log_files_in_group',  'Redo Log 文件组数',     'innodb', FALSE, 'count',
     'Redo Log 文件数量，实际 Redo Log 总大小 = log_file_size × files_in_group。', '5.6'),
    ('innodb_flush_log_at_trx_commit', '日志刷盘策略',      'innodb', TRUE,  '',
     '0=每秒刷；1=每次提交刷（最安全）；2=每次写OS缓冲。', '5.6')
ON CONFLICT (param_name) DO NOTHING;

-- 查询与缓存
INSERT INTO mysql_param_meta (param_name, display_name, category, is_dynamic, unit, description, min_version, max_version)
VALUES
    ('max_allowed_packet',  '最大允许包大小', 'general',    TRUE,  'bytes',
     '服务器/客户端可接收的最大数据包大小，建议 ≥ 64MB。', '5.6', NULL),
    ('table_open_cache',    '表缓存大小',     'general',    TRUE,  'count',
     '全局打开表句柄缓存数量，值应 > max_connections × 每个连接平均打开表数。', '5.6', NULL),
    ('open_files_limit',    '最大文件句柄数', 'general',    FALSE, 'count',
     '操作系统级文件描述符上限（含表文件 + Socket + binlog 等）。', '5.6', NULL),
    ('tmp_table_size',      '内存临时表上限', 'general',    TRUE,  'bytes',
     '内存临时表大小上限，超出后转磁盘临时表（性能下降）。与 max_heap_table_size 取较小值。', '5.6', NULL),
    ('query_cache_size',    '查询缓存大小',   'general',    TRUE,  'bytes',
     '查询缓存分配内存（5.6/5.7）。8.0 已彻底移除 Query Cache。高并发写场景应设为 0 禁用。',
     '5.6', '5.7')
ON CONFLICT (param_name) DO NOTHING;

-- 日志
INSERT INTO mysql_param_meta (param_name, display_name, category, is_dynamic, unit, description, min_version)
VALUES
    ('max_binlog_size',     'Binlog 最大文件大小', 'logging', TRUE,  'bytes',
     '单个 Binlog 文件最大大小，达到上限后自动轮换。', '5.6'),
    ('long_query_time',     '慢查询阈值',          'logging', TRUE,  'seconds',
     '超过此秒数的查询被记录到慢查询日志。支持小数（如 0.5 表示 500ms）。', '5.6'),
    ('sync_binlog',         'Binlog 同步策略',     'logging', TRUE,  '',
     '0=OS决定；1=每次提交同步（最安全，有性能开销）；N=每N次提交同步一次。', '5.6'),
    ('binlog_format',       'Binlog 格式',         'logging', TRUE,  '',
     'ROW/STATEMENT/MIXED。ROW 最安全，STATEMENT 日志量小，MIXED 自动选择。', '5.6'),
    ('slow_query_log',      '慢查询日志开关',      'logging', TRUE,  '',
     '是否开启慢查询日志（ON/OFF）。开启后超过 long_query_time 的语句被记录。', '5.6'),
    ('log_error',           '错误日志文件路径',    'logging', FALSE, '',
     '错误日志文件路径。若为空，日志输出到标准错误或 OS syslog。', '5.6'),
    ('general_log',         '通用查询日志开关',    'logging', TRUE,  '',
     '记录所有 SQL 语句（ON/OFF）。会产生大量 IO，生产环境慎开。', '5.6')
ON CONFLICT (param_name) DO NOTHING;

-- 字符集 / 时区
INSERT INTO mysql_param_meta (param_name, display_name, category, is_dynamic, unit, description, min_version)
VALUES
    ('character_set_server', '服务器字符集', 'general', FALSE, '',
     '服务器默认字符集，新建数据库未指定时使用此值。', '5.6'),
    ('time_zone',             '服务器时区',   'general', TRUE,  '',
     '服务器时区，影响 NOW()/SYSDATE() 等函数。建议显式设置与应用保持一致。', '5.6')
ON CONFLICT (param_name) DO NOTHING;

-- GTID / 复制安全
INSERT INTO mysql_param_meta (param_name, display_name, category, is_dynamic, unit, description, min_version)
VALUES
    ('log_bin',                  'Binlog 开关',     'logging',  FALSE, '',
     '是否开启 Binlog（ON/OFF）。主从复制、PIT 恢复都需要开启。', '5.6'),
    ('gtid_mode',                'GTID 模式',       'general', FALSE, '',
     'GTID 复制模式（OFF/OFF_PERMISSIVE/ON_PERMISSIVE/ON）。ON 模式下主从配置更简单。', '5.6'),
    ('enforce_gtid_consistency',  'GTID 强制一致性', 'general', FALSE, '',
     '是否强制 GTID 一致性（OFF/WARN/ON）。ON 时拒绝不兼容 GTID 的语句。', '5.6'),
    ('sql_mode',                 'SQL 模式',        'general', TRUE,  '',
     'SQL 严格模式设置，影响数据类型检查、空值处理等。建议包含 STRICT_TRANS_TABLES。', '5.6'),
    ('version',                  'MySQL 版本',      'general', FALSE, '',
     'MySQL Server 版本号（只读）。', '5.6')
ON CONFLICT (param_name) DO NOTHING;
