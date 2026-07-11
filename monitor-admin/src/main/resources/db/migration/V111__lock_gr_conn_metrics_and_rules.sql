-- =============================================================
-- V111：补齐锁超时 / MGR 采集指标定义 + 新增连接类内置告警规则
--
-- 背景：
--   1. builtin.lock_timeout.warning / builtin.gr.member_state.critical 两条内置规则
--      此前无对应采集项，本版本新增 LockTimeoutItem / GroupReplicationItem 落地，
--      此处补齐 metric_definition（规则与 metric_ref 已存在，无需变更）。
--   2. 对标竞品"性能预警设置"，新增三条连接类内置规则：
--      连接数激增 / 连接数峰值 / 活跃连接数百分比。
--      （"总连接数百分比"由既有 builtin.conn.usage.warning/critical 覆盖；
--        "复制进程状态/延迟"由既有 io_stopped/sql_stopped/replication.delay 覆盖）
-- =============================================================

-- ---- 1. 指标定义：锁超时 + MGR 成员状态 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.innodb.lock_timeout_count',
 '行锁等待超时次数（本周期）',
 'mysql', 'lock', 'guard', 'numeric', 'count',
 'mysql.lock_timeout', 'delta', '1m',
 'information_schema.INNODB_METRICS lock_timeouts 计数器的本采集周期增量，'
 '反映本分钟内因 innodb_lock_wait_timeout 超时失败的行锁等待次数（5.6+ 通用）'),
('mysql.gr.member_state',
 'MGR 成员状态',
 'mysql', 'replication', 'guard', 'numeric', 'count',
 'mysql.group_replication', 'raw', '1m',
 'performance_schema.replication_group_members 中本节点状态：ONLINE=1，'
 'RECOVERING/OFFLINE/ERROR/UNREACHABLE=0；未启用 MGR 时不产出（仅 8.0+）')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 指标定义：连接类新指标 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.delta.threads_connected',
 '连接数周期增量',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'mysql.global_status', 'delta', '1m',
 'Threads_connected 相比上一采集周期的增加量（下降时不产点），'
 '用于识别连接数短时间激增（如连接风暴、连接池配置异常）'),
('mysql.conn.active_pct',
 '活跃连接数百分比',
 'mysql', 'connection', 'guard', 'numeric', 'percent',
 'mysql.connections', 'ratio', '1m',
 '= 活跃(非 Sleep)连接数 / max_connections × 100，'
 '反映真正在执行请求的会话相对连接上限的占用程度')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. 新增内置规则：当前连接数激增 ----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '当前连接数激增',
    'builtin.conn.surge.warning',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.delta.threads_connected',
    '{"operator":">=","threshold":50.0,"unit":"count"}',
    '{"operator":"<","threshold":50.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '单采集周期内连接数增加 ≥ 50 个时触发预警，提示可能出现连接风暴或应用连接池异常'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 4. 新增内置规则：连接数峰值 ----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '连接数峰值',
    'builtin.conn.peak.warning',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.status.Threads_connected',
    '{"operator":">=","threshold":500.0,"unit":"count"}',
    '{"operator":"<","threshold":450.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '当前连接总数（Threads_connected）≥ 500 个时触发预警，接近连接上限前提前干预'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 5. 新增内置规则：活跃连接数百分比过高 ----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '活跃连接数百分比过高',
    'builtin.conn.active_pct.warning',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.conn.active_pct',
    '{"operator":">=","threshold":70.0,"unit":"percent"}',
    '{"operator":"<","threshold":60.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '活跃(非 Sleep)连接数占 max_connections 的比例 ≥ 70% 时触发预警，'
    '说明大量会话同时在执行请求，数据库可能已接近处理能力上限'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 6. 新规则的指标关联 ----
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
FROM alert_rule ar
WHERE ar.rule_code IN (
    'builtin.conn.surge.warning',
    'builtin.conn.peak.warning',
    'builtin.conn.active_pct.warning'
)
ON CONFLICT (rule_id, metric_code) DO NOTHING;
