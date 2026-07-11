-- =============================================================
-- V99: Top SQL 诊断维度增量列（还原原型慢SQL分析页所需字段）
--   源列：performance_schema.events_statements_summary_by_digest 的
--   SUM_LOCK_TIME / SUM_SORT_ROWS / SUM_NO_INDEX_USED /
--   SUM_CREATED_TMP_TABLES / SUM_CREATED_TMP_DISK_TABLES（5.7/8.0 均可用）
--   采集侧计算周期增量后落库；历史行为 NULL（升级前无采集）
-- =============================================================

ALTER TABLE metric_top_sql
    ADD COLUMN IF NOT EXISTS delta_lock_time       BIGINT,
    ADD COLUMN IF NOT EXISTS delta_sort_rows       BIGINT,
    ADD COLUMN IF NOT EXISTS delta_no_index_used   BIGINT,
    ADD COLUMN IF NOT EXISTS delta_tmp_tables      BIGINT,
    ADD COLUMN IF NOT EXISTS delta_tmp_disk_tables BIGINT;

COMMENT ON COLUMN metric_top_sql.delta_lock_time       IS '周期内锁等待时间增量（皮秒，SUM_LOCK_TIME 差值）';
COMMENT ON COLUMN metric_top_sql.delta_sort_rows       IS '周期内排序行数增量（SUM_SORT_ROWS 差值）';
COMMENT ON COLUMN metric_top_sql.delta_no_index_used   IS '周期内未使用索引的执行次数增量（SUM_NO_INDEX_USED 差值）';
COMMENT ON COLUMN metric_top_sql.delta_tmp_tables      IS '周期内创建内存临时表数增量（SUM_CREATED_TMP_TABLES 差值）';
COMMENT ON COLUMN metric_top_sql.delta_tmp_disk_tables IS '周期内创建磁盘临时表数增量（SUM_CREATED_TMP_DISK_TABLES 差值）';
