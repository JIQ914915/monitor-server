-- =============================================================
-- V53：补录采集项扩展指标的 metric_definition
--
-- 1. InnoDB Buffer Pool 绝对字节数（InnodbBufferPoolItem 新增）
-- 2. Buffer Pool 刷页速率（GlobalStatusItem COUNTERS 新增）
-- 3. VariablesItem 新增参数（tmp_table_size / query_cache_size / log_error / general_log）
-- =============================================================

-- ---- 1. Buffer Pool 绝对字节数 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.innodb.buffer_pool_bytes_data',
 'Buffer Pool 数据字节数',
 'mysql', 'innodb', 'analysis', 'numeric', 'bytes',
 'mysql.innodb_buffer_pool', 'raw', '1m',
 'Innodb_buffer_pool_bytes_data：Buffer Pool 中数据页占用的字节数（含 clean 与 dirty 页）；'
 '可与 innodb_buffer_pool_size 配合展示"已用 GB / 总 GB"'),
('mysql.innodb.buffer_pool_bytes_dirty',
 'Buffer Pool 脏页字节数',
 'mysql', 'innodb', 'analysis', 'numeric', 'bytes',
 'mysql.innodb_buffer_pool', 'raw', '1m',
 'Innodb_buffer_pool_bytes_dirty：Buffer Pool 中脏页（已修改未刷盘）占用的字节数')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. Buffer Pool 刷页速率 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.rate.Innodb_buffer_pool_pages_flushed',
 'Buffer Pool 刷页速率',
 'mysql', 'innodb', 'analysis', 'numeric', 'pages/s',
 'mysql.global_status', 'rate', '1m',
 'Innodb_buffer_pool_pages_flushed 计数器的每秒增量（页/秒）；'
 '高刷页率配合高脏页比例，可判断 I/O 写压力')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. VariablesItem 新增参数 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.var.tmp_table_size',
 '内存临时表上限',
 'mysql', 'config', 'explain', 'numeric', 'bytes',
 'mysql.global_variables', 'raw', '1d',
 'tmp_table_size 参数值（字节）；内存临时表超过此大小后转为 OnDisk 磁盘临时表，'
 '配合 Created_tmp_disk_tables 速率判断是否需要调大'),
('mysql.var.query_cache_size',
 '查询缓存大小',
 'mysql', 'config', 'explain', 'numeric', 'bytes',
 'mysql.global_variables', 'raw', '1d',
 'query_cache_size 参数值（字节）；MySQL 5.6/5.7 特有，8.0 已废弃；'
 '0 表示关闭；多核高并发场景建议设为 0 以避免全局互斥锁瓶颈'),
('mysql.var_text.log_error',
 '错误日志路径',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'log_error 参数值；MySQL 错误日志文件路径，变更时覆盖存储，用于配置巡检'),
('mysql.var_text.general_log',
 '通用查询日志开关',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'general_log 参数值（ON/OFF）；开启时记录所有 SQL，对性能影响极大；'
 '生产环境应为 OFF，变更时覆盖存储，用于配置变更审计')
ON CONFLICT (metric_code) DO NOTHING;
