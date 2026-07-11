-- =============================================================
-- V46：metric_definition 增量补充（P2-1 安全巡检 / P2-3 Binlog / P2-4 可用性）
--
-- 1. 可用性指标（AvailabilityItem，1m）
-- 2. Binlog 磁盘占用（BinlogStatusItem，1h）
-- 3. 配置域扩展（VariablesItem 新增数值 + 文本参数，1d）
-- 4. 安全巡检指标（SecurityAuditItem，1d）
-- =============================================================

-- ---- 1. 可用性域 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.availability',
 '实例可用性',
 'mysql', 'availability', 'guard', 'numeric', 'count',
 'mysql.availability', 'raw', '1m',
 '1 = 实例连接可达，0 = 连接失败（超时/拒绝/认证失败）；连续 3 次为 0 时实例 status 自动标为 abnormal')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. Binlog 占用（P2-3）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.binlog.total_bytes',
 'Binlog 总占用',
 'mysql', 'replication', 'analysis', 'numeric', 'bytes',
 'mysql.binlog_status', 'raw', '1h',
 'SHOW BINARY LOGS 所有文件大小之和（binlog 磁盘占用），需 REPLICATION CLIENT 或 SUPER 权限')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. 配置域扩展（VariablesItem 新增项，P2-3）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.var.innodb_log_files_in_group',
 'InnoDB Redo 日志文件数',
 'mysql', 'config', 'explain', 'numeric', 'count',
 'mysql.global_variables', 'raw', '1d',
 'innodb_log_files_in_group 参数值；Redo 日志总大小 = 此值 × innodb_log_file_size'),
('mysql.var.max_binlog_size',
 'Binlog 单文件上限',
 'mysql', 'config', 'explain', 'numeric', 'bytes',
 'mysql.global_variables', 'raw', '1d',
 'max_binlog_size 参数值（单个 binlog 文件的最大字节数）'),
('mysql.var_text.binlog_format',
 'Binlog 格式',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'binlog_format 参数值（ROW/STATEMENT/MIXED），变更时覆盖存储，用于配置变更审计'),
('mysql.var_text.gtid_mode',
 'GTID 模式',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'gtid_mode 参数值（OFF/OFF_PERMISSIVE/ON_PERMISSIVE/ON），变更时覆盖存储'),
('mysql.var_text.enforce_gtid_consistency',
 'GTID 一致性强制',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'enforce_gtid_consistency 参数值（ON/OFF/WARN）'),
('mysql.var_text.slow_query_log',
 '慢查询日志开关',
 'mysql', 'config', 'explain', 'text', NULL,
 'mysql.global_variables', 'state', '1d',
 'slow_query_log 参数值（ON/OFF），变更时覆盖存储，用于审计慢查询日志是否开启')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 4. 安全巡检域（SecurityAuditItem，P2-1）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.security.empty_password_count',
 '空密码账号数',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.security_audit', 'raw', '1d',
 '密码为空或 NULL 的非锁定账号数量；建议为 0，否则存在未授权访问风险'),
('mysql.security.any_host_account_count',
 '宽泛授权账号数（Host=%）',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.security_audit', 'raw', '1d',
 'Host=''%'' 的账号数，意味着可从任意 IP 连接；建议明确限定来源 IP'),
('mysql.security.super_priv_count',
 'SUPER 权限账号数',
 'mysql', 'security', 'analysis', 'numeric', 'count',
 'mysql.security_audit', 'raw', '1d',
 '拥有 SUPER 权限的账号数（不含匿名账号）；建议仅 DBA 账号持有，数量宜最小化'),
('mysql.security.anonymous_user_count',
 '匿名账号数',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.security_audit', 'raw', '1d',
 'User='''' 的匿名账号数；建议为 0，匿名账号存在高安全风险')
ON CONFLICT (metric_code) DO NOTHING;
