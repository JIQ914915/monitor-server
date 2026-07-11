-- =============================================================
-- V52：metric_long_conn 时间列名统一（对齐 V29 约定 collect_time）
--
-- V49 建表时用了 ts，与其他时序表（metric_data_1m / metric_capacity_object 等）
-- 在 V29 统一后的 collect_time 不一致；本迁移补做重命名。
-- TimescaleDB 支持对超表时间维度列重命名，无需特殊处理。
-- =============================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'metric_long_conn' AND column_name = 'ts'
    ) THEN
        ALTER TABLE metric_long_conn RENAME COLUMN ts TO collect_time;
        -- 重建索引以反映新列名（DROP 旧索引名，重建带新名的索引）
        DROP INDEX IF EXISTS idx_long_conn_instance_ts;
        DROP INDEX IF EXISTS idx_long_conn_time;
        CREATE INDEX idx_long_conn_instance_ct ON metric_long_conn (instance_id, collect_time DESC);
        CREATE INDEX idx_long_conn_time_ct      ON metric_long_conn (instance_id, time_seconds DESC, collect_time DESC);
    END IF;
END $$;
