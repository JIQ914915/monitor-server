-- =============================================================
-- V122: 慢SQL样本 sql_text 口径修正（仅注释，无结构变更）
--   采集端放开自身 4000 字符截断（保护性上限提高到 64K）；
--   实际截断来自目标库 performance_schema_max_sql_text_length（默认 1024），
--   被目标库截断的语句以 "..." 结尾。
-- =============================================================
COMMENT ON COLUMN metric_slow_sql_sample.sql_text IS
    '真实执行 SQL（含参数值）；长度受目标库 performance_schema_max_sql_text_length（默认 1024）限制，超长语句由 MySQL 截断并以 ... 结尾';
