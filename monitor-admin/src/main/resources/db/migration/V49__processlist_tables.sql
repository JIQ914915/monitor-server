-- =============================================================
-- V49：Processlist 采集域（P3）
--   1. metric_long_conn — 长连接明细超表（按 ts 分块）
--   2. metric_definition — 连接状态分布 / 长连接摘要 / 来源聚合指标定义
-- =============================================================

-- ---- 1. 长连接明细表 ----
CREATE TABLE metric_long_conn (
    instance_id   BIGINT        NOT NULL,
    conn_id       BIGINT        NOT NULL,  -- processlist id
    conn_user     VARCHAR(64),
    conn_host     VARCHAR(256),
    conn_db       VARCHAR(128),
    command       VARCHAR(32),
    time_seconds  INT,                     -- 连接持续秒数（TIME 列）
    state         VARCHAR(128),
    info          TEXT,                    -- 当前执行 SQL（截断到 2000 字符）
    ts            TIMESTAMPTZ   NOT NULL
);
COMMENT ON TABLE metric_long_conn IS '长连接明细（processlist time >= 30s 的连接，分钟级快照）';
CREATE INDEX idx_long_conn_instance_ts ON metric_long_conn (instance_id, ts DESC);
CREATE INDEX idx_long_conn_time        ON metric_long_conn (instance_id, time_seconds DESC, ts DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('metric_long_conn', 'ts', if_not_exists => TRUE);
        -- 保留 24 小时（明细数据价值期短）
        PERFORM add_retention_policy('metric_long_conn',
            INTERVAL '24 hours', if_not_exists => TRUE);
    END IF;
END $$;

-- ---- 2. metric_definition — 连接状态分布 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.conn.state.sleep',  '空闲连接数（Sleep）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 'processlist 中 Command=Sleep 的连接数'),
('mysql.conn.state.query',  '查询中连接数（Query）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 'processlist 中 Command=Query 的连接数'),
('mysql.conn.state.locked', '等锁连接数（Locked/Wait）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 'processlist 中 State 包含 Locked / Waiting for lock 的连接数'),
('mysql.conn.state.other',  '其他状态连接数',
 'mysql', 'connection', 'analysis', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 'processlist 中除 Sleep/Query/Locked 外的其他 Command 连接数'),
-- ---- 长连接摘要 ----
('mysql.conn.long_running_count', '长连接数（≥30s）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 '持续时间 ≥ 30 秒的连接数量（不含 Sleep）；过多长连接可能耗尽连接池或持有锁'),
('mysql.conn.max_duration_seconds', '最长连接持续秒数',
 'mysql', 'connection', 'analysis', 'numeric', 'seconds',
 'mysql.processlist', 'raw', '1m',
 'processlist 中 TIME 列的最大值（秒），反映最长存活连接的年龄')
ON CONFLICT (metric_code) DO NOTHING;
