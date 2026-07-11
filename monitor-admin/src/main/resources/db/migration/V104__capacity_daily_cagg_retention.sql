-- =============================================================
-- V104: capacity_instance_daily 连续聚合补默认保留策略
--   V51 创建该 cagg 时未设保留策略，物化数据会无限增长。
--   默认对齐天级口径（2 年，与 metric_data_1d_cagg 一致）；
--   后续由数据保留模块的 daily 类别统一管理（RetentionPolicyApplier）。
--   纯 PG 环境下该对象是普通物化视图，不支持保留策略，跳过。
-- =============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb')
       AND EXISTS (SELECT 1 FROM timescaledb_information.continuous_aggregates
                   WHERE view_name = 'capacity_instance_daily') THEN
        PERFORM add_retention_policy('capacity_instance_daily',
            INTERVAL '2 years', if_not_exists => TRUE);
    END IF;
END $$;
