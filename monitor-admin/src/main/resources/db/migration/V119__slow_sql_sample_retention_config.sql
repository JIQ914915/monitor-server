-- =============================================================
-- V118: 慢SQL真实执行样本纳入系统保留策略管理
--   metric_slow_sql_sample 原由 V101 硬编码 7 天保留策略；
--   新增 retention_config 类别 slow_sql_sample（默认 7 天），
--   由 RetentionPolicyApplier / RetentionStartupSync 统一下发。
-- =============================================================

INSERT INTO retention_config (category, retention_days, enabled)
VALUES ('slow_sql_sample', 7, TRUE)
ON CONFLICT (category) DO NOTHING;

COMMENT ON TABLE metric_slow_sql_sample IS
    '慢SQL真实执行样本（events_statements_history 分钟级采集；保留天数由 retention_config.slow_sql_sample 统一管理）';
