-- =============================================================
-- 时序采样表 metric_sample（§21.2）
-- 若安装了 TimescaleDB 扩展则转为 Hypertable（自动分块），否则为普通表。
-- =============================================================

CREATE TABLE metric_sample (
    instance_id BIGINT       NOT NULL,
    metric      VARCHAR(128) NOT NULL,
    value       DOUBLE PRECISION NOT NULL,
    ts          TIMESTAMPTZ  NOT NULL
);
COMMENT ON TABLE metric_sample IS '标准化时序采样指标点（采集器写入）';

-- 查询模式：按 实例 + 指标 + 时间范围 检索
CREATE INDEX idx_metric_sample_main ON metric_sample (instance_id, metric, ts DESC);

-- 存在 TimescaleDB 扩展时转 Hypertable（按 ts 自动分块）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('metric_sample', 'ts', if_not_exists => TRUE);
    END IF;
END $$;
