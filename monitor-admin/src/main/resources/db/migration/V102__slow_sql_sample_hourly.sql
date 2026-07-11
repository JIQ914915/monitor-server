-- =============================================================
-- V102: 慢SQL样本采集频率调整为小时级（与 Top SQL 指纹同频）
--   SlowSqlSampleItem 由分钟级改为 HOURLY，本迁移仅同步表/列注释，
--   表结构与保留策略不变。
--   小时级下两轮采集间隔更长，期间被 events_statements_history
--   （每线程环形缓冲，默认 10 条）覆盖的慢语句不会被捕获，抽样性更明显。
-- =============================================================

COMMENT ON TABLE metric_slow_sql_sample IS
    '慢SQL真实执行样本（events_statements_history 中耗时>=long_query_time 的语句，小时级采集与 Top SQL 指纹同频，保留 7 天）';
COMMENT ON COLUMN metric_slow_sql_sample.collect_time IS
    '采集时间（小时级）；history 每线程仅保留最近 10 条，样本为抽样而非全量';
