-- ===========================================================================
-- V42: metric_top_sql 新增差值列（P1-3 top_sql 差值加工）
--
-- 背景：原 metric_top_sql 仅存累积快照（count_star/sum_timer_wait 等为自实例启动后累积值），
--   无法直接使用。v2.0 决定在采集侧（TopSqlItem + TopSqlDeltaStore）对相邻两次快照做差值，
--   新增 delta_* 列存本周期增量，avg_timer_wait_us 供前端直接排序查询。
--   首次采集或计数器回绕时 delta 列写入 NULL（由写入层控制跳过）。
-- ===========================================================================

ALTER TABLE metric_top_sql
    ADD COLUMN IF NOT EXISTS delta_count         BIGINT,       -- 周期内执行次数增量
    ADD COLUMN IF NOT EXISTS delta_timer_wait    BIGINT,       -- 周期内总耗时增量（皮秒）
    ADD COLUMN IF NOT EXISTS avg_timer_wait_us   BIGINT,       -- 周期内平均单次耗时（微秒）= delta_timer_wait/1000/delta_count
    ADD COLUMN IF NOT EXISTS delta_rows_examined BIGINT,       -- 周期内扫描行数增量
    ADD COLUMN IF NOT EXISTS delta_rows_sent     BIGINT;       -- 周期内返回行数增量

COMMENT ON COLUMN metric_top_sql.delta_count         IS '周期内执行次数增量（NULL=首次采样/回绕）';
COMMENT ON COLUMN metric_top_sql.delta_timer_wait    IS '周期内总耗时增量（皮秒）';
COMMENT ON COLUMN metric_top_sql.avg_timer_wait_us   IS '周期内平均单次耗时（微秒），= delta_timer_wait/1000/delta_count';
COMMENT ON COLUMN metric_top_sql.delta_rows_examined IS '周期内扫描行数增量';
COMMENT ON COLUMN metric_top_sql.delta_rows_sent     IS '周期内返回行数增量';

-- 为 Top SQL 排名查询新增差值列索引（按平均耗时降序查询最热 SQL）
CREATE INDEX IF NOT EXISTS idx_metric_top_sql_avg_wait
    ON metric_top_sql (instance_id, collect_time DESC, avg_timer_wait_us DESC);

CREATE INDEX IF NOT EXISTS idx_metric_top_sql_delta_count
    ON metric_top_sql (instance_id, collect_time DESC, delta_count DESC);
