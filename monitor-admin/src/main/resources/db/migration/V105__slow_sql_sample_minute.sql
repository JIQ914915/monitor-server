-- =============================================================
-- V105: 慢SQL样本采集频率调整回分钟级
--   SlowSqlSampleItem 由小时级（V102）改回分钟级：history 每线程仅保留
--   最近 10 条语句，分钟级采集能显著缩小环形缓冲被覆盖的窗口、少漏样本；
--   查询本身轻量（最多 线程数 × 10 行且按耗时阈值过滤），对目标库无明显压力。
--   本迁移仅同步表/列注释，表结构与保留策略不变。
-- =============================================================

COMMENT ON TABLE metric_slow_sql_sample IS
    '慢SQL真实执行样本（events_statements_history 中耗时>=long_query_time 的语句，分钟级采集，保留 7 天）';
COMMENT ON COLUMN metric_slow_sql_sample.collect_time IS
    '采集时间（分钟级）；history 每线程仅保留最近 10 条，样本为抽样而非全量';
