-- =============================================================
-- V55: 临时表周期增量指标定义（P2-2 GlobalStatusItem.EXTRA_DELTA 新增项）
-- =============================================================

INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.delta.Created_tmp_tables',
 '内存临时表周期增量',
 'mysql', 'sql', 'analysis', 'numeric', 'count',
 'mysql.global_status', 'delta', '1m',
 '本采集周期内新创建的内存临时表数量（SHOW GLOBAL STATUS Created_tmp_tables 差值）；'
 '可汇总为今日累计，结合 tmp_table_size 判断是否频繁溢出'),
('mysql.delta.Created_tmp_disk_tables',
 '磁盘临时表周期增量',
 'mysql', 'sql', 'analysis', 'numeric', 'count',
 'mysql.global_status', 'delta', '1m',
 '本采集周期内因内存不足转磁盘的临时表数量（Created_tmp_disk_tables 差值）；'
 '越多说明内存临时表上限（tmp_table_size）不足，需关注')
ON CONFLICT (metric_code) DO NOTHING;
