-- =============================================================
-- V51：容量增长趋势（P3）
--
-- 1. capacity_instance_daily   — 实例维度日级容量连续聚合视图（TimescaleDB CA）
--    按 instance_id + day 分桶，对 metric_capacity_object 中
--    capacity.total_size_bytes（object_type=table）求和，得到实例每日总容量快照。
--
-- 2. capacity_weekly_growth    — 基于 capacity_instance_daily 的 7 日环比视图
--    比较当日容量与 7 天前容量，计算增长字节数和增长率（%）。
--
-- 降级策略：
--   若未安装 TimescaleDB，无法创建 CA；此时创建等效的普通聚合物化视图
--   capacity_instance_daily_plain（需手动 REFRESH 或定时任务刷新）。
--   capacity_weekly_growth 在两种环境下均为普通 VIEW，引用对应的物化视图。
-- =============================================================

DO $$
DECLARE
    has_ts BOOLEAN;
BEGIN
    SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') INTO has_ts;

    IF has_ts THEN
        -- ---- TimescaleDB 路径：连续聚合 + 自动刷新策略 ----
        EXECUTE $CA$
            CREATE MATERIALIZED VIEW IF NOT EXISTS capacity_instance_daily
            WITH (timescaledb.continuous) AS
            SELECT
                instance_id,
                time_bucket('1 day'::INTERVAL, collect_time) AS day,
                SUM(value)                                    AS total_bytes
            FROM metric_capacity_object
            WHERE metric_code = 'capacity.total_size_bytes'
              AND object_type = 'table'
            GROUP BY instance_id, day
            WITH NO DATA
        $CA$;

        -- 自动刷新：每小时刷新过去 8 天数据（7 天对比 + 1 天宽限）
        PERFORM add_continuous_aggregate_policy(
            'capacity_instance_daily',
            start_offset  => INTERVAL '8 days',
            end_offset    => INTERVAL '1 hour',
            schedule_interval => INTERVAL '1 hour',
            if_not_exists => TRUE
        );

    ELSE
        -- ---- 降级路径：普通物化视图（需外部定时 REFRESH） ----
        EXECUTE $MV$
            CREATE MATERIALIZED VIEW IF NOT EXISTS capacity_instance_daily AS
            SELECT
                instance_id,
                date_trunc('day', collect_time AT TIME ZONE 'UTC') AS day,
                SUM(value)                                          AS total_bytes
            FROM metric_capacity_object
            WHERE metric_code = 'capacity.total_size_bytes'
              AND object_type = 'table'
            GROUP BY instance_id, day
            WITH NO DATA
        $MV$;

        -- 创建索引以加速 JOIN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_cap_daily_inst_day
                 ON capacity_instance_daily (instance_id, day)';
    END IF;
END $$;

-- ---- 7 日环比视图（TimescaleDB / 普通环境通用） ----
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
