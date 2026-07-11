-- =============================================================
-- V148：修复实例日级容量被重复累加的问题
--
-- 背景：capacity_object 采集项为小时级，每小时对每张表写入一条
--   capacity.total_size_bytes 快照。V51 的 capacity_instance_daily
--   对全天数据直接 SUM(value)，等于把一天内最多 24 次快照全部相加，
--   导致"当前库表总容量 / 日均增长"被放大约 24 倍（如 12GB 显示 281GB）。
--
-- 修复口径：每表每天取"最后一次快照"（last），再按实例求和。
--   1. capacity_object_daily    — 表级日快照（instance_id + object_name + day，
--                                  取当日最后一次采集值），TimescaleDB 下为连续聚合；
--   2. capacity_instance_daily  — 改为普通视图：对 capacity_object_daily 按实例求和；
--   3. capacity_weekly_growth   — 定义不变，重建（依赖对象被级联删除）。
--
-- 保留策略：capacity_object_daily 默认 2 年，后续由数据保留模块 daily 类别统一管理。
-- =============================================================

-- 旧对象级联清理（capacity_weekly_growth 依赖 capacity_instance_daily，一并删除后重建）
DROP VIEW IF EXISTS capacity_weekly_growth;
DROP MATERIALIZED VIEW IF EXISTS capacity_instance_daily CASCADE;

DO $$
DECLARE
    has_ts BOOLEAN;
BEGIN
    SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO has_ts;

    IF has_ts THEN
        -- ---- TimescaleDB 路径：表级日快照连续聚合 ----
        -- materialized_only=false：策略窗口（8 天）之外的历史查询实时回落到原始表，
        -- 避免迁移后旧数据出现空窗。
        EXECUTE $CA$
            CREATE MATERIALIZED VIEW IF NOT EXISTS capacity_object_daily
            WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
            SELECT
                instance_id,
                object_name,
                time_bucket('1 day'::INTERVAL, collect_time) AS day,
                last(value, collect_time)                     AS total_bytes
            FROM metric_capacity_object
            WHERE metric_code = 'capacity.total_size_bytes'
              AND object_type = 'table'
            GROUP BY instance_id, object_name, day
            WITH NO DATA
        $CA$;

        -- 自动刷新：每小时刷新过去 8 天数据（7 天环比 + 1 天宽限）
        PERFORM add_continuous_aggregate_policy(
            'capacity_object_daily',
            start_offset  => INTERVAL '8 days',
            end_offset    => INTERVAL '1 hour',
            schedule_interval => INTERVAL '1 hour',
            if_not_exists => TRUE
        );

        -- 默认保留 2 年（与天级口径一致，可被数据保留模块覆盖）
        PERFORM add_retention_policy('capacity_object_daily',
            INTERVAL '2 years', if_not_exists => TRUE);

    ELSE
        -- ---- 降级路径：普通物化视图（需外部定时 REFRESH），DISTINCT ON 取当日末次快照 ----
        EXECUTE $MV$
            CREATE MATERIALIZED VIEW IF NOT EXISTS capacity_object_daily AS
            SELECT DISTINCT ON (instance_id, object_name,
                                date_trunc('day', collect_time AT TIME ZONE 'UTC'))
                instance_id,
                object_name,
                date_trunc('day', collect_time AT TIME ZONE 'UTC') AS day,
                value                                              AS total_bytes
            FROM metric_capacity_object
            WHERE metric_code = 'capacity.total_size_bytes'
              AND object_type = 'table'
            ORDER BY instance_id, object_name,
                     date_trunc('day', collect_time AT TIME ZONE 'UTC'),
                     collect_time DESC
            WITH NO DATA
        $MV$;

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_cap_obj_daily_inst_day
                 ON capacity_object_daily (instance_id, day)';
    END IF;
END $$;

-- ---- 实例日级容量：表级末次快照按实例求和（两种环境通用的普通视图） ----
CREATE VIEW capacity_instance_daily AS
SELECT instance_id, day, SUM(total_bytes) AS total_bytes
FROM capacity_object_daily
GROUP BY instance_id, day;

COMMENT ON VIEW capacity_instance_daily IS
    '实例日级库表总容量（每表取当日末次快照后求和，修复 V51 全天快照重复累加问题）';

-- ---- 7 日环比视图（与 V51 定义一致） ----
CREATE OR REPLACE VIEW capacity_weekly_growth AS
SELECT
    a.instance_id,
    a.day                                         AS current_day,
    a.total_bytes                                 AS current_bytes,
    b.total_bytes                                 AS prev_week_bytes,
    (a.total_bytes - COALESCE(b.total_bytes, 0))  AS growth_bytes,
    CASE
        WHEN COALESCE(b.total_bytes, 0) > 0
        THEN ROUND(
            ((a.total_bytes - b.total_bytes) / b.total_bytes * 100)::NUMERIC,
            2)
        ELSE NULL
    END                                           AS growth_rate_pct
FROM capacity_instance_daily a
LEFT JOIN capacity_instance_daily b
       ON a.instance_id = b.instance_id
      AND b.day = a.day - INTERVAL '7 days';

COMMENT ON VIEW capacity_weekly_growth IS
    '实例容量 7 日环比（当日总容量 vs 7 天前），用于容量增长趋势展示';
