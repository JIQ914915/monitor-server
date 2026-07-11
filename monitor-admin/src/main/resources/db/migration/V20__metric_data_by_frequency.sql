-- =============================================================
-- 数值时序按频率分表 metric_data_1m / 1h / 1d（§21.2.2 / §21.2.3 / §12.2）
-- 分表的根本原因：TimescaleDB 保留/压缩策略以「每张 Hypertable 一套」为粒度，
-- 分级差异化保留（分钟级 30 天 / 小时级 180 天 / 天级 2 年）必须落到不同表。
-- 采集器按 CollectFrequency 路由写入对应表。
-- 存在 TimescaleDB 扩展则转 Hypertable 并注册压缩/保留策略，否则降级为普通表。
-- =============================================================

-- ---- 分钟级：chunk 7 天、7 天后压缩、保留 30 天 ----
CREATE TABLE metric_data_1m (
    instance_id  BIGINT           NOT NULL,
    metric_code  VARCHAR(128)     NOT NULL,
    value        DOUBLE PRECISION NOT NULL,
    collect_time TIMESTAMPTZ      NOT NULL,
    created_at   TIMESTAMPTZ      DEFAULT now(),
    PRIMARY KEY (instance_id, metric_code, collect_time)
);
COMMENT ON TABLE metric_data_1m IS '分钟级数值时序（原始/差值速率/比例），保留30天';
CREATE INDEX idx_metric_data_1m_metric_time ON metric_data_1m (metric_code, collect_time DESC);
CREATE INDEX idx_metric_data_1m_inst_time ON metric_data_1m (instance_id, collect_time DESC);

-- ---- 小时级：chunk 30 天、30 天后压缩、保留 180 天 ----
CREATE TABLE metric_data_1h (
    instance_id  BIGINT           NOT NULL,
    metric_code  VARCHAR(128)     NOT NULL,
    value        DOUBLE PRECISION NOT NULL,
    collect_time TIMESTAMPTZ      NOT NULL,
    created_at   TIMESTAMPTZ      DEFAULT now(),
    PRIMARY KEY (instance_id, metric_code, collect_time)
);
COMMENT ON TABLE metric_data_1h IS '小时级数值时序（独立采集的容量等 + 由1m降采样），保留180天';
CREATE INDEX idx_metric_data_1h_metric_time ON metric_data_1h (metric_code, collect_time DESC);
CREATE INDEX idx_metric_data_1h_inst_time ON metric_data_1h (instance_id, collect_time DESC);

-- ---- 天级：chunk 365 天、90 天后压缩、保留 2 年 ----
CREATE TABLE metric_data_1d (
    instance_id  BIGINT           NOT NULL,
    metric_code  VARCHAR(128)     NOT NULL,
    value        DOUBLE PRECISION NOT NULL,
    collect_time TIMESTAMPTZ      NOT NULL,
    created_at   TIMESTAMPTZ      DEFAULT now(),
    PRIMARY KEY (instance_id, metric_code, collect_time)
);
COMMENT ON TABLE metric_data_1d IS '天级数值时序（配置/安全/容量趋势），保留2年';
CREATE INDEX idx_metric_data_1d_metric_time ON metric_data_1d (metric_code, collect_time DESC);
CREATE INDEX idx_metric_data_1d_inst_time ON metric_data_1d (instance_id, collect_time DESC);

-- ---- 存在 TimescaleDB 扩展时：转 Hypertable + 空间分区 + 压缩/保留策略 ----
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        -- 分钟级
        PERFORM create_hypertable('metric_data_1m', 'collect_time',
                                  chunk_time_interval => INTERVAL '7 days', if_not_exists => TRUE);
        PERFORM add_dimension('metric_data_1m', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
        ALTER TABLE metric_data_1m SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_data_1m', INTERVAL '7 days');
        PERFORM add_retention_policy('metric_data_1m', INTERVAL '30 days');

        -- 小时级
        PERFORM create_hypertable('metric_data_1h', 'collect_time',
                                  chunk_time_interval => INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_dimension('metric_data_1h', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
        ALTER TABLE metric_data_1h SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_data_1h', INTERVAL '30 days');
        PERFORM add_retention_policy('metric_data_1h', INTERVAL '180 days');

        -- 天级
        PERFORM create_hypertable('metric_data_1d', 'collect_time',
                                  chunk_time_interval => INTERVAL '365 days', if_not_exists => TRUE);
        PERFORM add_dimension('metric_data_1d', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
        ALTER TABLE metric_data_1d SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_data_1d', INTERVAL '90 days');
        PERFORM add_retention_policy('metric_data_1d', INTERVAL '2 years');
    END IF;
END $$;
