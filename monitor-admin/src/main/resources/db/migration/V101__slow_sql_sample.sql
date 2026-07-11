-- =============================================================
-- V101: 慢SQL真实执行样本表
--   数据源：performance_schema.events_statements_history（5.7/8.0 默认开启，
--   每线程保留最近 10 条语句），分钟级采集耗时 >= long_query_time 的真实 SQL。
--   去重由采集侧内存记录 (thread_id, event_id) 水位完成，表侧不设唯一约束
--   （TimescaleDB 超表唯一索引必须包含分区列，无法表达跨 chunk 去重）。
-- =============================================================

CREATE TABLE metric_slow_sql_sample (
    instance_id     BIGINT        NOT NULL,
    thread_id       BIGINT        NOT NULL,   -- P_S THREAD_ID（与 event_id 组成采集侧去重键）
    event_id        BIGINT        NOT NULL,   -- P_S EVENT_ID（线程内单调递增）
    conn_user       VARCHAR(64),              -- 执行账号
    conn_host       VARCHAR(256),             -- 来源主机
    schema_name     VARCHAR(128),
    digest          VARCHAR(128),             -- 归一化指纹（关联 metric_top_sql / 指纹分析）
    sql_text        TEXT,                     -- 真实执行 SQL（含参数，截断到 4000 字符）
    exec_time_us    BIGINT,                   -- 执行耗时（微秒）
    lock_time_us    BIGINT,                   -- 锁等待（微秒）
    rows_examined   BIGINT,
    rows_sent       BIGINT,
    sort_rows       BIGINT,
    no_index_used   BOOLEAN,                  -- 本次执行是否未使用索引
    tmp_tables      BIGINT,                   -- 创建临时表数（内存+磁盘）
    tmp_disk_tables BIGINT,                   -- 创建磁盘临时表数
    collect_time    TIMESTAMPTZ   NOT NULL    -- 采集时间（近似执行结束时间，分钟级）
);

COMMENT ON TABLE metric_slow_sql_sample IS
    '慢SQL真实执行样本（events_statements_history 中耗时>=long_query_time 的语句，分钟级采集，保留 7 天）';
COMMENT ON COLUMN metric_slow_sql_sample.sql_text IS '真实执行 SQL（含参数值，截断 4000 字符）';
COMMENT ON COLUMN metric_slow_sql_sample.collect_time IS '采集时间；history 每线程仅保留最近 10 条，样本为抽样而非全量';

CREATE INDEX idx_slow_sample_instance_time ON metric_slow_sql_sample (instance_id, collect_time DESC);
CREATE INDEX idx_slow_sample_digest        ON metric_slow_sql_sample (instance_id, digest, collect_time DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('metric_slow_sql_sample', 'collect_time', if_not_exists => TRUE);
        -- 样本明细价值期短，保留 7 天；历史分析走 metric_top_sql 指纹聚合（180 天）
        PERFORM add_retention_policy('metric_slow_sql_sample',
            INTERVAL '7 days', if_not_exists => TRUE);
    END IF;
END $$;
