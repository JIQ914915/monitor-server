-- =============================================================
-- V114：补齐高价值内置告警——死锁 / 实例重启 / 连接被拒
--
-- 采集侧配套（本版本代码变更）：
--   1. LockTimeoutItem 增采 INNODB_METRICS lock_deadlocks → mysql.innodb.deadlock_count
--   2. GlobalStatusItem 基于 Uptime 下降检测重启 → mysql.instance.restarted（1/0）
--   3. GlobalStatusItem EXTRA_DELTA 增加 Aborted_connects / Connection_errors_max_connections
--      → mysql.delta.aborted_connects / mysql.delta.connection_errors_max_connections
-- =============================================================

-- ---- 1. 指标定义 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.innodb.deadlock_count',
 '死锁次数（本周期）',
 'mysql', 'lock', 'guard', 'numeric', 'count',
 'mysql.lock_timeout', 'delta', '1m',
 'information_schema.INNODB_METRICS lock_deadlocks 计数器的本采集周期增量，'
 '反映本分钟内发生的 InnoDB 死锁次数（5.6+ 通用）'),
('mysql.instance.restarted',
 '实例重启标记',
 'mysql', 'availability', 'guard', 'numeric', 'count',
 'mysql.global_status', 'state', '1m',
 '基于 SHOW GLOBAL STATUS Uptime 判定：本轮 Uptime 小于上轮 = 两次采集之间实例发生过重启（=1），'
 '否则 =0；Collector 重启后首轮无基线不产出'),
('mysql.delta.aborted_connects',
 '连接失败次数（本周期）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.global_status', 'delta', '1m',
 'Aborted_connects 的本采集周期增量：客户端连接失败次数（密码错误、权限不足、握手超时等），'
 '持续偏高可能存在配置错误的应用或口令扫描'),
('mysql.delta.connection_errors_max_connections',
 '连接数打满拒绝次数（本周期）',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.global_status', 'delta', '1m',
 'Connection_errors_max_connections 的本采集周期增量：因达到 max_connections 上限而被拒绝的连接数，'
 '大于 0 说明连接数已打满、业务请求正在被拒绝')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 内置规则：死锁频发 ----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '死锁频发',
    'builtin.deadlock.warning',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.innodb.deadlock_count',
    '{"operator":">=","threshold":3.0,"unit":"count"}',
    '{"operator":"<","threshold":1.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '单采集周期内 InnoDB 死锁次数 ≥ 3，事务相互等待被强制回滚，'
    '建议结合死锁日志排查事务加锁顺序与索引设计'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 3. 内置规则：实例发生重启（布尔型状态规则）----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '实例发生重启',
    'builtin.instance.restarted',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.instance.restarted',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"检测到实例发生重启（Uptime 下降）时立即触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"实例运行时长恢复正常增长时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '两次采集之间实例运行时长（Uptime）变小，说明数据库发生过重启；'
    '计划外重启请检查错误日志、OOM 与主机状态'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 4. 内置规则：连接失败次数过多 ----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '连接失败次数过多',
    'builtin.aborted_connects.warning',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.delta.aborted_connects',
    '{"operator":">=","threshold":10.0,"unit":"count"}',
    '{"operator":"<","threshold":5.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '单采集周期内客户端连接失败（Aborted_connects）≥ 10 次，'
    '常见原因：应用账号密码配置错误、权限不足、网络异常或口令扫描'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 5. 内置规则：连接数打满拒绝连接 ----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '连接数打满拒绝连接',
    'builtin.conn.rejected.critical',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.delta.connection_errors_max_connections',
    '{"operator":">=","threshold":1.0,"unit":"count"}',
    '{"operator":"<","threshold":1.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '本采集周期内出现因达到 max_connections 上限而被拒绝的连接，业务请求正在失败；'
    '请立即排查连接占用（Sleep 连接、连接池泄漏）或评估调大 max_connections'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 6. 新规则的指标关联 ----
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN (
     'builtin.deadlock.warning',
     'builtin.instance.restarted',
     'builtin.aborted_connects.warning',
     'builtin.conn.rejected.critical'
 )
ON CONFLICT (rule_id, metric_code) DO NOTHING;
