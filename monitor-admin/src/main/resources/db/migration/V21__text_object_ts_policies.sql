-- =============================================================
-- 为 V19 的文本/对象/TopSQL 表补齐 TimescaleDB 空间分区 + 压缩 + 保留策略（§12.2 / §21.2.3）
-- 保留期归属：
--   metric_text_data       文本/状态变更历史（配置/复制审计价值高、量小）→ 180 天
--   metric_top_sql         小时级采集的 Top SQL 快照 → 180 天（与小时级一致）
--   metric_capacity_object 小时级采集的对象容量明细 → 180 天（与小时级一致）
-- 仅在安装了 TimescaleDB 扩展时生效；普通表环境跳过（保留期由后续清理任务兜底）。
-- =============================================================

-- 说明：add_dimension 要求 Hypertable「尚无 chunk」，而 V19 建表后采集器可能已写入并产生 chunk，
-- 直接调用会报 "cannot add dimension ... already has chunks"。故对空间分区加「无 chunk 才添加」守卫；
-- 压缩/保留策略对有数据的表允许，正常注册（重复注册用 if_not_exists 幂等）。
DO $$
DECLARE
    has_chunks BOOLEAN;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        -- ---- 文本/状态指标：按 (instance_id, metric) 压缩，保留 180 天 ----
        SELECT EXISTS (SELECT 1 FROM timescaledb_information.chunks
                       WHERE hypertable_name = 'metric_text_data') INTO has_chunks;
        IF NOT has_chunks THEN
            PERFORM add_dimension('metric_text_data', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
        ELSE
            RAISE NOTICE 'metric_text_data 已有 chunk，跳过 add_dimension（空间分区仅能在无数据时添加）';
        END IF;
        ALTER TABLE metric_text_data SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, metric',
            timescaledb.compress_orderby = 'ts DESC');
        PERFORM add_compression_policy('metric_text_data', INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metric_text_data', INTERVAL '180 days', if_not_exists => TRUE);

        -- ---- Top SQL 快照（小时级）：按 (instance_id, digest) 压缩，30 天后压缩，保留 180 天 ----
        SELECT EXISTS (SELECT 1 FROM timescaledb_information.chunks
                       WHERE hypertable_name = 'metric_top_sql') INTO has_chunks;
        IF NOT has_chunks THEN
            PERFORM add_dimension('metric_top_sql', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
        ELSE
            RAISE NOTICE 'metric_top_sql 已有 chunk，跳过 add_dimension';
        END IF;
        ALTER TABLE metric_top_sql SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, digest',
            timescaledb.compress_orderby = 'ts DESC');
        PERFORM add_compression_policy('metric_top_sql', INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metric_top_sql', INTERVAL '180 days', if_not_exists => TRUE);

        -- ---- 对象级容量明细：按 (instance_id, object_type) 压缩，保留 180 天 ----
        SELECT EXISTS (SELECT 1 FROM timescaledb_information.chunks
                       WHERE hypertable_name = 'metric_capacity_object') INTO has_chunks;
        IF NOT has_chunks THEN
            PERFORM add_dimension('metric_capacity_object', 'instance_id', number_partitions => 4, if_not_exists => TRUE);
        ELSE
            RAISE NOTICE 'metric_capacity_object 已有 chunk，跳过 add_dimension';
        END IF;
        ALTER TABLE metric_capacity_object SET (timescaledb.compress,
            timescaledb.compress_segmentby = 'instance_id, object_type',
            timescaledb.compress_orderby = 'ts DESC');
        PERFORM add_compression_policy('metric_capacity_object', INTERVAL '30 days', if_not_exists => TRUE);
        PERFORM add_retention_policy('metric_capacity_object', INTERVAL '180 days', if_not_exists => TRUE);
    END IF;
END $$;
