-- =============================================================
-- V45：metric_definition 增量补充（P1-5 新采集项 + V24 编码漂移修复）
--
-- 1. 修复 V24 编码漂移：data_length→data_size_bytes、index_length→index_size_bytes
--    （采集器实际落库编码与 V24 定义不一致，会导致前端单位/描述查询失效）
-- 2. 补充 LockWaitsItem 产出的锁等待指标
-- 3. 补充 InnodbBufferPoolItem 产出的 Buffer Pool 派生指标
-- 4. 补充 ConnectionsItem 产出的连接三件套（V24 遗漏）
-- 5. 补充容量域的 total_size_bytes
-- =============================================================

-- ---- 1. 修复 V24 容量域编码漂移 ----
UPDATE metric_definition
   SET metric_code = 'mysql.capacity.data_size_bytes',
       metric_name = '数据容量',
       updated_at  = now()
 WHERE metric_code = 'mysql.capacity.data_length';

UPDATE metric_definition
   SET metric_code = 'mysql.capacity.index_size_bytes',
       metric_name = '索引容量',
       updated_at  = now()
 WHERE metric_code = 'mysql.capacity.index_length';

-- ---- 2. 锁等待域（LockWaitsItem，1m，§P1-5）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.innodb.lock_waits',
 '锁等待数',
 'mysql', 'lock', 'guard', 'numeric', 'count',
 'mysql.lock_waits', 'raw', '1m',
 '当前 InnoDB 锁等待关系总数（等待-阻塞对数量）；5.6 使用 information_schema，5.7 使用 sys，8.0 使用 data_lock_waits'),
('mysql.innodb.blocked_sessions',
 '阻塞会话数',
 'mysql', 'lock', 'guard', 'numeric', 'count',
 'mysql.lock_waits', 'raw', '1m',
 '被锁等待阻塞的唯一事务数（distinct 等待方事务 ID）')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. InnoDB Buffer Pool 派生指标（InnodbBufferPoolItem，1m，§P1-5/P2-3）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.innodb.buffer_pool_hit_rate',
 'Buffer Pool 命中率',
 'mysql', 'innodb', 'guard', 'numeric', 'percent',
 'mysql.innodb_buffer_pool', 'ratio', '1m',
 '=(1 - 物理磁盘读/逻辑读请求) × 100，基于服务启动以来累积计数器，值越高越好（建议 ≥ 99%）'),
('mysql.innodb.dirty_page_ratio',
 '脏页比例',
 'mysql', 'innodb', 'analysis', 'numeric', 'percent',
 'mysql.innodb_buffer_pool', 'ratio', '1m',
 '= 脏页数/总页数 × 100；持续高位（>75%）表明刷新压力大，可能影响写性能'),
('mysql.innodb.buffer_pool_usage',
 'Buffer Pool 使用率',
 'mysql', 'innodb', 'analysis', 'numeric', 'percent',
 'mysql.innodb_buffer_pool', 'ratio', '1m',
 '= (总页数 - 空闲页) / 总页数 × 100；长期低于 80% 可考虑缩减 innodb_buffer_pool_size')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 4. ConnectionsItem 产出的连接三件套（V24 遗漏）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.conn.total',
 '总连接数（processlist）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.connections', 'raw', '1m',
 '来自 SHOW PROCESSLIST 的实时连接总数（含 Sleep）'),
('mysql.conn.active',
 '活跃连接数',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.connections', 'raw', '1m',
 '来自 SHOW PROCESSLIST 的非 Sleep 连接数，反映当前真正活跃的会话'),
('mysql.conn.max',
 '最大连接数（运行时）',
 'mysql', 'connection', 'explain', 'numeric', 'count',
 'mysql.connections', 'raw', '1m',
 '从 max_connections 系统变量读取的运行时最大连接数')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 5. 补充容量域 total_size_bytes ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.capacity.total_size_bytes',
 '总容量（数据+索引）',
 'mysql', 'capacity', 'analysis', 'numeric', 'bytes',
 'mysql.information_schema.tables', 'raw', '1h',
 '业务库数据+索引总大小（data_size + index_size）')
ON CONFLICT (metric_code) DO NOTHING;
