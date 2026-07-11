-- =============================================================
-- 表结构整改（二）：指标定义/元数据表 metric_definition（需求 §21.2.2）
--   定位：数值/文本落表路由 + 单位格式化 + 指标释义的「单一事实来源」。
--   写入与查询均依据 value_type 将 metric_code 落到 metric_data_*（numeric）或 metric_text_data_*（text）。
--   新表遵循 §21.2 约定：TIMESTAMPTZ + created_at/updated_at + set_updated_at() 触发器。
-- =============================================================

CREATE TABLE metric_definition (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    metric_code      VARCHAR(100) NOT NULL,                 -- 指标编码，如 mysql.connection.usage_percent
    metric_name      VARCHAR(100) NOT NULL,                 -- 指标名称，如 连接使用率
    db_type          VARCHAR(20)  NOT NULL,                 -- 数据库类型（mysql/oracle/...）
    domain           VARCHAR(50),                           -- 指标域（connection/traffic/sql/lock/innodb/replication/capacity/security/collector）
    layer            VARCHAR(20),                           -- 分层（guard 值守 / analysis 分析 / explain 解释）
    value_type       VARCHAR(20)  NOT NULL DEFAULT 'numeric', -- 值类型：numeric 数值 / text 文本状态
    unit             VARCHAR(20),                           -- 单位（percent/count/bytes/ms/qps...），前端格式化用
    source_collector VARCHAR(100),                          -- 来源采集器编码（collector_config.collector_code）
    process_type     VARCHAR(20),                           -- 加工类型（raw/delta/ratio/agg/trend/state/score，与 §9.1 对应）
    frequency        VARCHAR(10),                           -- 采集频率（1m/1h/1d），辅助路由到 metric_data_<频率>
    description      TEXT,                                  -- 指标释义（是什么/单位/如何解读），前端 Tooltip；场景诊断归知识库
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_metric_definition_code UNIQUE (metric_code)
);
COMMENT ON TABLE metric_definition IS '指标定义/元数据表（数值/文本路由、单位格式化、指标释义单一来源）';

CREATE INDEX idx_metric_definition_db_type    ON metric_definition (db_type);
CREATE INDEX idx_metric_definition_domain     ON metric_definition (domain);
CREATE INDEX idx_metric_definition_value_type ON metric_definition (value_type);

CREATE TRIGGER trg_metric_definition_updated_at BEFORE UPDATE ON metric_definition
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- 初始化：与已实现的 MySQL 采集项对齐（数值 + 文本），其余随采集器演进增量补充 ----
INSERT INTO metric_definition (metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description) VALUES
-- 连接域
('mysql.conn.usage',            '连接使用率',        'mysql', 'connection', 'guard',    'numeric', 'percent', 'mysql.global_status',   'ratio', '1m', '当前连接数/最大连接数'),
('mysql.status.Threads_connected','当前连接数',      'mysql', 'connection', 'guard',    'numeric', 'count',   'mysql.global_status',   'raw',   '1m', '当前已建立的连接数'),
('mysql.status.Threads_running', '活跃线程数',        'mysql', 'connection', 'guard',    'numeric', 'count',   'mysql.global_status',   'raw',   '1m', '正在执行的线程数'),
-- 流量域（累积计数器差值速率）
('mysql.qps',                    'QPS',              'mysql', 'traffic',    'guard',    'numeric', 'qps',     'mysql.global_status',   'delta', '1m', '每秒查询数（Questions 差值/间隔）'),
('mysql.tps',                    'TPS',              'mysql', 'traffic',    'guard',    'numeric', 'qps',     'mysql.global_status',   'delta', '1m', '每秒事务数（Com_commit+Com_rollback 差值/间隔）'),
-- InnoDB / 事务
('mysql.innodb.trx_active',      '活跃事务数',        'mysql', 'innodb',     'analysis', 'numeric', 'count',   'mysql.innodb_trx',      'raw',   '1m', 'information_schema.innodb_trx 活跃事务数'),
('mysql.innodb.trx_max_seconds', '最长事务时长',      'mysql', 'innodb',     'analysis', 'numeric', 'count',   'mysql.innodb_trx',      'raw',   '1m', '当前最长运行事务的持续秒数'),
-- 复制域（数值 + 文本）
('mysql.replication.is_replica', '是否从库',          'mysql', 'replication','guard',    'numeric', 'count',   'mysql.replica_status',  'raw',   '1m', '是否配置为从库：1 是 / 0 否'),
('mysql.replication.seconds_behind','复制延迟',       'mysql', 'replication','guard',    'numeric', 'count',   'mysql.replica_status',  'raw',   '1m', '从库落后主库秒数'),
('mysql.replication.io_running', 'IO 线程运行',       'mysql', 'replication','guard',    'numeric', 'count',   'mysql.replica_status',  'raw',   '1m', 'IO 线程是否运行：1/0'),
('mysql.replication.sql_running','SQL 线程运行',      'mysql', 'replication','guard',    'numeric', 'count',   'mysql.replica_status',  'raw',   '1m', 'SQL 线程是否运行：1/0'),
('mysql.replication.last_error', '复制错误信息',      'mysql', 'replication','analysis', 'text',    NULL,      'mysql.replica_status',  'state', '1m', '最近复制错误文本（覆盖变更存储）'),
-- 容量域（小时级）
('mysql.capacity.data_length',   '数据容量',          'mysql', 'capacity',   'analysis', 'numeric', 'bytes',   'mysql.information_schema.tables', 'raw', '1h', '业务库数据总大小'),
('mysql.capacity.index_length',  '索引容量',          'mysql', 'capacity',   'analysis', 'numeric', 'bytes',   'mysql.information_schema.tables', 'raw', '1h', '业务库索引总大小'),
('mysql.capacity.table_count',   '表数量',            'mysql', 'capacity',   'analysis', 'numeric', 'count',   'mysql.information_schema.tables', 'raw', '1h', '业务库表数量'),
-- 配置域（天级，数值 + 文本）
('mysql.var.max_connections',    '最大连接数配置',    'mysql', 'config',     'explain',  'numeric', 'count',   'mysql.global_variables','raw',   '1d', 'max_connections 参数值'),
('mysql.var_text.version',       '数据库版本',        'mysql', 'config',     'explain',  'text',    NULL,      'mysql.global_variables','state', '1d', 'version 参数文本（覆盖变更存储）'),
('mysql.var_text.sql_mode',      'SQL 模式',          'mysql', 'config',     'explain',  'text',    NULL,      'mysql.global_variables','state', '1d', 'sql_mode 参数文本（覆盖变更存储）');
