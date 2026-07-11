-- =============================================================
-- V113：内置规则版本适用性与版本术语修正（V112 复制规则拆分的延续排查）
--
--   1. builtin.lock_timeout.warning：
--      V61 创建时计划用 performance_schema 统计，版本限定为 5.7+；
--      实际落地的 LockTimeoutItem 采集 information_schema.INNODB_METRICS
--      的 lock_timeouts 计数器，MySQL 5.6 起即可用 → 放开为全版本。
--   2. builtin.binlog.size.critical：
--      描述中的 expire_logs_days 在 8.0 已弃用（改用 binlog_expire_logs_seconds），
--      规则本身全版本通用，描述改为按版本区分参数名。
--   3. builtin.errorlog.error.critical：
--      "单采集周期内"表述含糊，两个版本口径不同（8.0 为最近 1 小时窗口计数，
--      5.7 为 events_errors_summary 的小时增量），描述说明清楚。
-- =============================================================

-- ---- 1. 锁等待超时：放开为 MySQL 全版本 ----
UPDATE alert_rule
   SET db_version_ids = NULL,
       description = '单采集周期内行锁等待超时（innodb_lock_wait_timeout）次数 ≥ 5，'
                     '说明存在高并发锁争用；数据来自 INNODB_METRICS lock_timeouts 计数器（MySQL 5.6+ 通用）',
       updated_at = now()
 WHERE rule_code = 'builtin.lock_timeout.warning';

-- ---- 2. binlog 空间占用：描述按版本区分清理参数 ----
UPDATE alert_rule
   SET description = 'binlog 总占用 ≥ 20 GB，建议清理或调整自动过期参数：'
                     '5.6/5.7 用 expire_logs_days，8.0+ 用 binlog_expire_logs_seconds',
       updated_at = now()
 WHERE rule_code = 'builtin.binlog.size.critical';

-- ---- 3. 错误日志错误数：描述说明两个版本的统计口径 ----
UPDATE alert_rule
   SET description = '错误日志 ERROR 级别事件数 ≥ 50（小时级采集）：'
                     '8.0 统计 performance_schema.error_log 最近 1 小时条数，'
                     '5.7 统计 events_errors_summary 的小时增量；5.6 不支持（错误日志仅落文件）',
       updated_at = now()
 WHERE rule_code = 'builtin.errorlog.error.critical';
