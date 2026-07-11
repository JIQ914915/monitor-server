-- =============================================================
-- 表结构整改（七）：文本/对象/TopSQL 表列名统一为 metric_code / collect_time（对齐 metric_data_*）
--   metric_text_data:       metric -> metric_code, ts -> collect_time
--   metric_top_sql:         ts -> collect_time
--   metric_capacity_object: metric -> metric_code, ts -> collect_time
-- 注意：ts/metric 被 TimescaleDB 压缩设置（compress_orderby/segmentby）引用，直接 RENAME 会被拒绝。
--   故先「移除压缩策略 → 解压已压缩 chunk → 关闭压缩」，改名后再「按新列名开启压缩 → 重加策略」。
--   保留策略(add_retention_policy)与列名无关，无需变更。无 TimescaleDB 时直接 RENAME。
-- =============================================================

DO $$
DECLARE
    ts_present BOOLEAN;
    r RECORD;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO ts_present;

    -- 1) 清理压缩（仅 TimescaleDB）：移策略 → 解压 → 关压缩，使被引用的列可改名
    IF ts_present THEN
        PERFORM remove_compression_policy('metric_text_data', if_exists => TRUE);
        PERFORM remove_compression_policy('metric_top_sql', if_exists => TRUE);
        PERFORM remove_compression_policy('metric_capacity_object', if_exists => TRUE);

        FOR r IN SELECT c FROM show_chunks('metric_text_data') AS c LOOP
            PERFORM decompress_chunk(r.c, TRUE);
        END LOOP;
        FOR r IN SELECT c FROM show_chunks('metric_top_sql') AS c LOOP
            PERFORM decompress_chunk(r.c, TRUE);
        END LOOP;
        FOR r IN SELECT c FROM show_chunks('metric_capacity_object') AS c LOOP
            PERFORM decompress_chunk(r.c, TRUE);
        END LOOP;

        ALTER TABLE metric_text_data       SET (timescaledb.compress = FALSE);
        ALTER TABLE metric_top_sql         SET (timescaledb.compress = FALSE);
        ALTER TABLE metric_capacity_object SET (timescaledb.compress = FALSE);
    END IF;

    -- 2) 改列名（带存在性守卫，幂等；TimescaleDB 支持改时间维度列名）
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'metric_text_data' AND column_name = 'metric') THEN
        ALTER TABLE metric_text_data RENAME COLUMN metric TO metric_code;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'metric_text_data' AND column_name = 'ts') THEN
        ALTER TABLE metric_text_data RENAME COLUMN ts TO collect_time;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'metric_top_sql' AND column_name = 'ts') THEN
        ALTER TABLE metric_top_sql RENAME COLUMN ts TO collect_time;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'metric_capacity_object' AND column_name = 'metric') THEN
        ALTER TABLE metric_capacity_object RENAME COLUMN metric TO metric_code;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'metric_capacity_object' AND column_name = 'ts') THEN
        ALTER TABLE metric_capacity_object RENAME COLUMN ts TO collect_time;
    END IF;

    -- 3) 按新列名恢复压缩 + 保留策略（仅 TimescaleDB）
    IF ts_present THEN
        ALTER TABLE metric_text_data SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric_code',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_text_data', INTERVAL '30 days', if_not_exists => TRUE);

        ALTER TABLE metric_top_sql SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, digest',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_top_sql', INTERVAL '30 days', if_not_exists => TRUE);

        ALTER TABLE metric_capacity_object SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, object_type',
            timescaledb.compress_orderby = 'collect_time DESC');
        PERFORM add_compression_policy('metric_capacity_object', INTERVAL '30 days', if_not_exists => TRUE);
    END IF;
END $$;
