-- =============================================================
-- 文本/状态指标 + 对象级/多维指标存储表（§9.1 覆盖变更、§21.2.5 对象级专用表）
-- 与 metric_sample 一致：存在 TimescaleDB 扩展则转 Hypertable，否则普通表。
-- =============================================================

-- -------------------------------------------------------------
-- 1) 文本/状态指标（覆盖变更存储）：复制状态/错误、参数文本、SQL 文本等
--    仅在 value_hash 变化时写入（由写入层去重），只保留变更历史。
-- -------------------------------------------------------------
CREATE TABLE metric_text_data (
    instance_id BIGINT       NOT NULL,
    metric      VARCHAR(128) NOT NULL,
    value_text  TEXT,
    value_hash  VARCHAR(64),
    ts          TIMESTAMPTZ  NOT NULL
);
COMMENT ON TABLE metric_text_data IS '文本/状态指标（覆盖变更存储，仅值变化时落库）';
CREATE INDEX idx_metric_text_main ON metric_text_data (instance_id, metric, ts DESC);

-- -------------------------------------------------------------
-- 2) Top SQL / digest 明细（对象级专用表）：累积快照，差值由处理层加工
-- -------------------------------------------------------------
CREATE TABLE metric_top_sql (
    instance_id    BIGINT       NOT NULL,
    schema_name    VARCHAR(128),
    digest         VARCHAR(64)  NOT NULL,
    digest_text    TEXT,
    count_star     BIGINT,
    sum_timer_wait BIGINT,
    rows_examined  BIGINT,
    rows_sent      BIGINT,
    ts             TIMESTAMPTZ  NOT NULL
);
COMMENT ON TABLE metric_top_sql IS 'Top SQL / 语句摘要累积快照（对象级，按 digest 维度）';
CREATE INDEX idx_metric_top_sql_main ON metric_top_sql (instance_id, digest, ts DESC);
CREATE INDEX idx_metric_top_sql_time ON metric_top_sql (instance_id, ts DESC);

-- -------------------------------------------------------------
-- 3) 对象级容量明细（对象级专用表）：库表/表空间/文件组等分对象容量
--    object_type + object_name 表达对象维度，禁止把对象名编入 metric（§21.2.5）。
-- -------------------------------------------------------------
CREATE TABLE metric_capacity_object (
    instance_id BIGINT       NOT NULL,
    metric      VARCHAR(128) NOT NULL,
    object_type VARCHAR(32)  NOT NULL,
    object_name VARCHAR(256) NOT NULL,
    value       DOUBLE PRECISION,
    ts          TIMESTAMPTZ  NOT NULL
);
COMMENT ON TABLE metric_capacity_object IS '对象级容量明细（表/表空间/数据文件/文件组等分对象指标）';
CREATE INDEX idx_metric_cap_obj_main ON metric_capacity_object (instance_id, object_type, object_name, ts DESC);
CREATE INDEX idx_metric_cap_obj_metric ON metric_capacity_object (instance_id, metric, ts DESC);

-- -------------------------------------------------------------
-- 存在 TimescaleDB 扩展时统一转 Hypertable（按 ts 自动分块）
-- -------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('metric_text_data', 'ts', if_not_exists => TRUE);
        PERFORM create_hypertable('metric_top_sql', 'ts', if_not_exists => TRUE);
        PERFORM create_hypertable('metric_capacity_object', 'ts', if_not_exists => TRUE);
    END IF;
END $$;
