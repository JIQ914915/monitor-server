-- =============================================================
-- V60：告警规则版本级绑定
--
-- 核心设计变更：
--   1. alert_rule 表新增 db_version 列（逗号分隔 version_code），
--      NULL 表示适用该 db_type 的所有版本。
--   2. 评估引擎（AlertEvaluateJobHandler）与服务层（AlertRuleServiceImpl）
--      在解析 scope_type=db_type 规则的目标实例时，同时比对实例版本
--      （database_version.version_code）是否在 db_version 列表中。
--   3. 新增若干 5.7/8.0 版本专属内置规则作为示例：
--      - performance_schema 相关指标（5.7.7+ 才有）
--      - 8.0 新增的 replication 源端统计
-- =============================================================

-- ---- 1. 新增 db_version 列 ----
ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS db_version VARCHAR(100);

COMMENT ON COLUMN alert_rule.db_version IS
    '适用版本（逗号分隔 version_code，如 5.7,8.0），NULL 表示适用该 db_type 所有版本';

-- ---- 2. 现有内置规则保持 NULL（适用所有 MySQL 版本）----
-- 无需 UPDATE，默认 NULL 即"适用所有版本"。

-- ---- 3. 新增版本专属内置规则 ----

-- [V60-1] 错误日志错误数异常（warning）[5.7+, 1m]
--   mysql.errorlog.error_count 来自 performance_schema.events_errors_summary_global_by_error
--   MySQL 5.7.7+ 起支持；5.6 无此表，采集时会跳过，指标不存在则评估返回 NORMAL 不误报
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, db_type, db_version, scope_config,
    notification_config, enabled, created_by, description)
VALUES (
    '错误日志错误数异常',
    'builtin.errorlog.error.warning',
    'builtin', 'warning', 'availability', 'mysql.errorlog.error_count',
    '{"operator":">=","threshold":10.0}',
    '{"operator":"<","threshold":3.0}',
    'db_type', 'MySQL', '5.7,8.0,8.4', NULL, NULL, TRUE, 'system',
    '单采集周期内 performance_schema 记录的 ERROR 级别事件数 ≥ 10 时触发预警（需 MySQL 5.7+）'
) ON CONFLICT (rule_code) DO NOTHING;

-- [V60-2] 错误日志错误数严重（critical）[5.7+, 1m]
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, db_type, db_version, scope_config,
    notification_config, enabled, created_by, description)
VALUES (
    '错误日志错误数严重',
    'builtin.errorlog.error.critical',
    'builtin', 'critical', 'availability', 'mysql.errorlog.error_count',
    '{"operator":">=","threshold":50.0}',
    '{"operator":"<","threshold":20.0}',
    'db_type', 'MySQL', '5.7,8.0,8.4', NULL, NULL, TRUE, 'system',
    '单采集周期内 performance_schema 记录的 ERROR 级别事件数 ≥ 50 时触发严重告警（需 MySQL 5.7+）'
) ON CONFLICT (rule_code) DO NOTHING;

-- [V60-3] 等待行锁超时次数过高（warning）[5.7+, 1m]
--   mysql.innodb.lock_timeout_count 来自 performance_schema.events_waits_summary_global_by_event_name
--   统计 innodb/lock_wait_timeout 等待超时累计次数（delta）
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, db_type, db_version, scope_config,
    notification_config, enabled, created_by, description)
VALUES (
    '锁等待超时次数过高',
    'builtin.lock_timeout.warning',
    'builtin', 'warning', 'performance', 'mysql.innodb.lock_timeout_count',
    '{"operator":">=","threshold":5.0}',
    '{"operator":"<","threshold":2.0}',
    'db_type', 'MySQL', '5.7,8.0,8.4', NULL, NULL, TRUE, 'system',
    '单采集周期内行锁等待超时次数 ≥ 5 时触发预警，说明存在高并发锁争用（需 MySQL 5.7+）'
) ON CONFLICT (rule_code) DO NOTHING;

-- [V60-4] 8.0 组复制成员状态异常（critical）[8.0+]
--   mysql.gr.member_state = 0 表示成员离线（GroupReplicationItem 产出，仅 8.0+ MGR 场景有值）
--   主库无 MGR 配置时指标不存在，evaluator 返回 null → NORMAL，不误报
INSERT INTO alert_rule (rule_name, rule_code, rule_type, rule_level, category, metric_name,
    condition_config, recovery_config, scope_type, db_type, db_version, scope_config,
    notification_config, enabled, created_by, description)
VALUES (
    'MGR 成员状态异常',
    'builtin.gr.member_state.critical',
    'builtin', 'critical', 'availability', 'mysql.gr.member_state',
    '{"operator":"<","threshold":1.0}',
    '{"operator":">=","threshold":1.0}',
    'db_type', 'MySQL', '8.0,8.4', NULL, NULL, TRUE, 'system',
    'MySQL Group Replication 成员状态异常（OFFLINE/ERROR），仅适用于 8.0+ MGR 集群'
) ON CONFLICT (rule_code) DO NOTHING;

-- =============================================================
-- 版本分布说明：
--   db_version IS NULL : 适用 MySQL 所有版本（5.6 / 5.7 / 8.0 / 8.4）
--   '5.7,8.0,8.4'      : 适用 MySQL 5.7 及以上（排除 5.6）
--   '8.0,8.4'          : 仅适用 MySQL 8.0+
-- =============================================================
