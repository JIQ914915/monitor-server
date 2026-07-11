-- =============================================================
-- V50：连接分析域 / 慢 SQL 增量 / 配置参数补全（P3 + P2）
--
-- 1. Processlist 连接来源对象指标（metric_capacity_object 复用）
-- 2. 慢 SQL 每周期增量指标（GlobalStatusItem 新增 extra_delta）
-- 3. VariablesItem 白名单数值/文本参数 metric_definition 补全
-- =============================================================

-- ---- 1. Processlist 连接状态分布 / 长连接摘要（已在 V49 中定义，本节补充来源聚合） ----
--   来源聚合走 metric_capacity_object 表（object_type='conn_source'），
--   metric_code 与 capacity 共用表格式，无需额外 metric_definition 条目。
--   以下为说明性注释，不插入重复条目。
-- (conn.source.total / conn.source.active 为 capacity_object 指标，不入 metric_definition)

-- ---- 2. 慢 SQL 每周期增量 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.delta.slow_queries',
 '慢查询新增数（本分钟）',
 'mysql', 'performance', 'guard', 'numeric', 'count',
 'mysql.global_status', 'delta', '1m',
 'SHOW GLOBAL STATUS Slow_queries 的本采集周期增量（两次快照之差），'
 '反映本分钟内新增的慢查询语句数；值 > 0 建议结合 long_query_time 阈值分析')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. VariablesItem 数值参数补全（WANTED 白名单全量对齐） ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.var.max_connections',
 '最大连接数',
 'mysql', 'config', 'explain', 'numeric', 'count',
 'mysql.global_variables', 'raw', '1d',
 'max_connections 参数值；实例允许的最大客户端连接数'),
('mysql.var.innodb_buffer_pool_size',
 'InnoDB Buffer Pool 大小',
 'mysql', 'config', 'explain', 'numeric', 'bytes',
 'mysql.global_variables', 'raw', '1d',
 'innodb_buffer_pool_size 参数值（字节）；通常建议设为可用内存的 70–80%'),
('mysql.var.innodb_log_file_size',
 'InnoDB Redo 日志单文件大小',
 'mysql', 'config', 'explain', 'numeric', 'bytes',
 'mysql.global_variables', 'raw', '1d',
 'innodb_log_file_size 参数值（字节）；Redo 总大小 = 此值 × innodb_log_files_in_group'),
('mysql.var.max_allowed_packet',
 '最大包大小',
 'mysql', 'config', 'explain', 'numeric', 'bytes',
 'mysql.global_variables', 'raw', '1d',
 'max_allowed_packet 参数值（字节）；超大包或 BLOB 操作时需调整'),
('mysql.var.table_open_cache',
 '表缓存大小',
 'mysql', 'config', 'explain', 'numeric', 'count',
 'mysql.global_variables', 'raw', '1d',
 'table_open_cache 参数值；允许同时打开的表描述符数量'),
('mysql.var.thread_cache_size',
 '线程缓存大小',
 'mysql', 'config', 'explain', 'numeric', 'count',
 'mysql.global_variables', 'raw', '1d',
 'thread_cache_size 参数值；缓存的空闲线程数，降低线程创建开销'),
('mysql.var.open_files_limit',
 '文件描述符上限',
 'mysql', 'config', 'explain', 'numeric', 'count',
 'mysql.global_variables', 'raw', '1d',
 'open_files_limit 参数值；MySQL 进程可使用的文件描述符上限'),
('mysql.var.wait_timeout',
 '非交互连接超时（秒）',
 'mysql', 'config', 'explain', 'numeric', 'seconds',
 'mysql.global_variables', 'raw', '1d',
 'wait_timeout 参数值；非交互连接空闲超过此秒数后被服务器主动断开'),
('mysql.var.long_query_time',
 '慢查询阈值（秒）',
 'mysql', 'config', 'explain', 'numeric', 'seconds',
 'mysql.global_variables', 'raw', '1d',
 'long_query_time 参数值；执行时间超过此值的查询会计入 Slow_queries 计数器')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- VariablesItem 文本参数补全（WANTED_TEXT 白名单全量对齐） ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.var_text.sql_mode',
 'SQL Mode',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'sql_mode 参数值；控制 SQL 语法校验严格程度，变更时覆盖存储'),
('mysql.var_text.version',
 'MySQL 版本',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'version 参数值（如 8.0.32），实例版本发生变化时覆盖存储'),
('mysql.var_text.time_zone',
 '时区设置',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'time_zone 参数值；SYSTEM 或具体时区（如 +08:00）'),
('mysql.var_text.character_set_server',
 '服务器字符集',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'character_set_server 参数值；影响数据库/表的默认字符集'),
('mysql.var_text.innodb_flush_log_at_trx_commit',
 'InnoDB Redo 刷盘策略',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'innodb_flush_log_at_trx_commit 参数值（0/1/2）；1 = 每次提交刷盘（最安全）'),
('mysql.var_text.sync_binlog',
 'Binlog 同步策略',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'sync_binlog 参数值；1 = 每次提交同步刷盘，0 = 依赖 OS 刷盘'),
('mysql.var_text.log_bin',
 'Binlog 开关',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'log_bin 参数值（ON/OFF）；是否开启 Binary Log，主从/PITR 必需')
ON CONFLICT (metric_code) DO NOTHING;
