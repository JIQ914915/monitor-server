-- =============================================================
-- V32: 文本/状态指标按频率分表 metric_text_data_1m / 1h / 1d（§21.2.5）
-- 动机与数值表一致：TimescaleDB 压缩/保留策略以「每张 Hypertable 一套」为粒度，
-- 文本状态按频率差异化保留（复制状态 1m 短存 / 配置参数 1d 长存）必须落到不同表。
-- 采集器按 CollectFrequency 路由写入（覆盖变更去重逻辑不变）。
-- 迁移：将旧 metric_text_data 存量数据并入 1m 表（默认频率），随后丢弃旧表。
-- =============================================================

-- ---- 分钟级：chunk 7 天、7 天后压缩、保留 30 天 ----
CREATE TABLE metric_text_data_1m (
    instance_id  BIGINT       NOT NULL,
    metric_code  VARCHAR(128) NOT NULL,
    value_text   TEXT,
    value_hash   VARCHAR(64),
    collect_time TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  DEFAULT now(),
    PRIMARY KEY (instance_id, metric_code, collect_time)
);
COMMENT ON TABLE metric_text_data_1m IS '分钟级文本/状态指标（覆盖变更，如复制状态/错误），保留30天';
CREATE INDEX idx_metric_text_1m_main ON metric_text_data_1m (instance_id, metric_code, collect_time DESC);

-- ---- 小时级：chunk 30 天、30 天后压缩、保留 180 天 ----
CREATE TABLE metric_text_data_1h (
    instance_id  BIGINT       NOT NULL,
    metric_code  VARCHAR(128) NOT NULL,
    value_text   TEXT,
    value_hash   VARCHAR(64),
    collect_time TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  DEFAULT now(),
    PRIMARY KEY (instance_id, metric_code, collect_time)
);
COMMENT ON TABLE metric_text_data_1h IS '小时级文本/状态指标（覆盖变更），保留180天';
CREATE INDEX idx_metric_text_1h_main ON metric_text_data_1h (instance_id, metric_code, collect_time DESC);

-- ---- 天级：chunk 365 天、90 天后压缩、保留 2 年 ----
CREATE TABLE metric_text_data_1d (
    instance_id  BIGINT       NOT NULL,
    metric_code  VARCHAR(128) NOT NULL,
    value_text   TEXT,
    value_hash   VARCHAR(64),
    collect_time TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  DEFAULT now(),
    PRIMARY KEY (instance_id, metric_code, collect_time)
);
COMMENT ON TABLE metric_text_data_1d IS '天级文本/状态指标（覆盖变更，如配置参数快照），保留2年';
CREATE INDEX idx_metric_text_1d_main ON metric_text_data_1d (instance_id, metric_code, collect_time DESC);

-- ---- 存在 TimescaleDB 扩展时：转 Hypertable + 空间分区（在迁移数据前，保持表为空）----
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('metric_text_data_1m', 'collect_time',
                                  chunk_time_interval => INTERVAL '7 days', if_not_exists => TRUE);
        PERFORM add_dimension('metric_text_data_1m', 'instance_id', number_partitions => 4, if_not_exists => TRUE);

        PERFORM create_hypertable('metric_text_data_1h', 'collect_time',
                                  chunk_time_interval => INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_dimension('metric_text_data_1h', 'instance_id', number_partitions => 4, if_not_exists => TRUE);

        PERFORM create_hypertable('metric_text_data_1d', 'collect_time',
                                  chunk_time_interval => INTERVAL '365 days', if_not_exists => TRUE);
        PERFORM add_dimension('metric_text_data_1d', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
    END IF;
END $$;

-- ---- 迁移旧 metric_text_data 存量数据 → 1m 表（默认频率）----
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'metric_text_data') THEN
        INSERT INTO metric_text_data_1m (instance_id, metric_code, value_text, value_hash, collect_time)
        SELECT instance_id, metric_code, value_text, value_hash, collect_time
        FROM metric_text_data
        ON CONFLICT (instance_id, metric_code, collect_time) DO NOTHING;
    END IF;
END $$;

-- ---- 压缩/保留策略（迁移完成后启用）----
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        ALTER TABLE metric_text_data_1m SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_text_data_1m', INTERVAL '7 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metric_text_data_1m', INTERVAL '30 days', if_not_exists => TRUE);

        ALTER TABLE metric_text_data_1h SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_text_data_1h', INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metric_text_data_1h', INTERVAL '180 days', if_not_exists => TRUE);

        ALTER TABLE metric_text_data_1d SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_text_data_1d', INTERVAL '90 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metric_text_data_1d', INTERVAL '2 years', if_not_exists => TRUE);
    END IF;
END $$;

-- ---- 丢弃旧单表（读取方尚未建立，写入层已切换为频率路由）----
DROP TABLE IF EXISTS metric_text_data;
