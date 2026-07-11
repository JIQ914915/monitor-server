-- =============================================================
-- 采集运行日志表：每次采集任务（实例×频率）完成后追加一条记录，
-- 支撑采集器管理页面的"日志"功能与统计展示。
-- 只追加不更新，无 updated_at 触发器。
--
-- 注意：TimescaleDB 超表要求主键必须包含分区列（collect_time），
--   故使用复合主键 (id, collect_time)。
-- =============================================================

CREATE TABLE collect_log (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    instance_id    BIGINT       NOT NULL,               -- 采集实例 ID
    frequency      VARCHAR(5)   NOT NULL,               -- 1m / 1h / 1d
    collect_time   TIMESTAMPTZ  NOT NULL DEFAULT now(), -- 采集开始时间
    duration_ms    INT,                                 -- 耗时（毫秒）
    metric_count   INT          NOT NULL DEFAULT 0,     -- 数值指标点数
    text_count     INT          NOT NULL DEFAULT 0,     -- 文本指标点数
    object_count   INT          NOT NULL DEFAULT 0,     -- 对象级指标点数
    success        BOOLEAN      NOT NULL,               -- 是否成功
    error_message  TEXT,                                -- 失败原因（成功时为空）
    PRIMARY KEY (id, collect_time)                      -- 复合主键：TimescaleDB 超表要求分区列在主键中
);

COMMENT ON TABLE collect_log IS '采集运行日志（每次采集写一条，append-only）';
CREATE INDEX idx_collect_log_instance_freq ON collect_log (instance_id, frequency, collect_time DESC);
CREATE INDEX idx_collect_log_time         ON collect_log (collect_time DESC);

-- TimescaleDB 超表（按月分区，与时序数据一致）
SELECT create_hypertable('collect_log', 'collect_time', if_not_exists => TRUE);

-- 保留策略：默认保留 90 天日志
SELECT add_retention_policy('collect_log', INTERVAL '90 days', if_not_exists => TRUE);
