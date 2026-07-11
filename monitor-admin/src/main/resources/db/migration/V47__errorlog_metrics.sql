-- =============================================================
-- V47：metric_definition 增量补充（P2-2 错误日志采集项）
--
-- ErrorLogItem（1h）产出指标：
-- 8.0：error_count / warning_count（过去 1h 计数）+ latest_error（文本）
-- 5.7：error_count（本轮 events_errors_summary 增量）
-- 5.6：不采集
-- =============================================================

INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.errorlog.error_count',
 '错误日志 Error 数',
 'mysql', 'stability', 'guard', 'numeric', 'count',
 'mysql.error_log', 'raw', '1h',
 '8.0：过去 1 小时内 performance_schema.error_log PRIO=Error 的条数；'
 || '5.7：events_errors_summary_global_by_error 累计 Error 本轮增量；'
 || '5.6 不采集。非零说明实例在该小时内出现了服务端错误，需关注'),
('mysql.errorlog.warning_count',
 '错误日志 Warning 数',
 'mysql', 'stability', 'analysis', 'numeric', 'count',
 'mysql.error_log', 'raw', '1h',
 '8.0 专属：过去 1 小时内 performance_schema.error_log PRIO=Warning 的条数；'
 || '持续偏高可能预示配置不当或资源瓶颈'),
('mysql.errorlog.latest_error',
 '最新 Error 日志',
 'mysql', 'stability', 'analysis', 'text', NULL,
 'mysql.error_log', 'state', '1h',
 '8.0 专属：performance_schema.error_log 中最新一条 Error 级别的 DATA 文本；'
 || '覆盖变更存储，仅在内容变化时落库，用于快速定位最近发生的严重错误')
ON CONFLICT (metric_code) DO NOTHING;
