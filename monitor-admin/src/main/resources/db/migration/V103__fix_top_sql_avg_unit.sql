-- =============================================================
-- V103: 修正 metric_top_sql.avg_timer_wait_us 单位错误
--   采集侧此前计算平均耗时时皮秒 → 微秒只除以 1000（实际需除以 1e6），
--   导致存量 avg_timer_wait_us 的值是真实微秒数的 1000 倍（即纳秒），
--   页面出现"最慢SQL 59.78min"这类放大 1000 倍的展示。
--   采集代码已修正（TopSqlDeltaStore），本迁移把存量数据缩回正确单位。
-- =============================================================

UPDATE metric_top_sql
SET avg_timer_wait_us = avg_timer_wait_us / 1000
WHERE avg_timer_wait_us IS NOT NULL;
