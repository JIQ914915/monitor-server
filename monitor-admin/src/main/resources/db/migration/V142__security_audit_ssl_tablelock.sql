-- =============================================================
-- V142：安全审计 / 来源白名单 / SSL 监控 / 表锁与缓存指标
--       （差距分析 2026-07-08 第四轮第一梯队）
--
-- 采集侧配套（本版本代码变更）：
--   1. StatementAuditItem（新增，1m，5.7/8.0）：权限变更/危险操作轻审计
--   2. AuthFailureItem（新增，1m，通用）：host_cache 认证失败来源 + 暴力破解疑似
--   3. ProcesslistItem：白名单外来源检测 + 线程状态细化
--   4. SslStatusItem（新增，1d，通用）：SSL 配置与证书有效期
--   5. OpenTablesItem（新增，1m，通用）：SHOW OPEN TABLES 表级锁
--   6. GlobalStatusItem：表缓存/查询缓存/表锁计数器
-- =============================================================

-- ---- 1. db_instance 增加连接来源白名单 ----
ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS conn_source_whitelist JSONB;

COMMENT ON COLUMN db_instance.conn_source_whitelist IS
    '连接来源白名单（IP 精确或 "10.0.1.*" 前缀通配的字符串数组）；非空时采集侧比对 processlist 来源并产出未知来源指标，空 = 不启用';

-- ---- 2. 安全审计指标 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.security.priv_change_delta',
 '权限变更语句数（本周期）',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.statement_audit', 'delta', '1m',
 '本采集周期内执行的权限变更语句次数（GRANT/REVOKE/CREATE USER/DROP USER/ALTER USER/'
 || 'RENAME USER/SET PASSWORD，基于 performance_schema 语句指纹计数差值）；5.7/8.0 采集'),
('mysql.security.dangerous_op_delta',
 '危险操作语句数（本周期）',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.statement_audit', 'delta', '1m',
 '本采集周期内执行的危险语句次数：DROP TABLE/DROP DATABASE/TRUNCATE 及不带 WHERE 的 '
 || 'DELETE/UPDATE（整表批量修改）；轻审计口径（无法还原执行账号），5.7/8.0 采集'),
('mysql.security.audit_events',
 '敏感语句审计明细',
 'mysql', 'security', 'analysis', 'text', NULL,
 'mysql.statement_audit', 'state', '1m',
 '本周期命中的权限变更/危险操作语句明细 JSON：[{type,text,schema,count,lastSeen}]（最多 20 条）'),
('mysql.security.auth_fail_delta',
 '认证失败次数（本周期）',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.auth_failure', 'delta', '1m',
 'performance_schema.host_cache 各来源 IP 认证失败（密码错误等）的本周期总增量；'
 || 'skip_name_resolve=ON 时主机缓存禁用、无数据，此时以 mysql.delta.aborted_connects 兜底'),
('mysql.security.brute_force_suspect',
 '暴力破解疑似标记',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.auth_failure', 'state', '1m',
 '复合判定（失败次数 + 来源集中度）：单一来源 IP 本周期认证失败 ≥ 5 次，'
 || '或全体来源合计 ≥ 15 次时置 1，否则 0'),
('mysql.security.auth_fail_sources',
 '认证失败来源明细',
 'mysql', 'security', 'analysis', 'text', NULL,
 'mysql.auth_failure', 'state', '1m',
 '本周期认证失败来源 Top 10 JSON：[{ip,delta,total}]，delta=本周期新增，total=累计'),
('mysql.security.unknown_source_count',
 '白名单外连接数',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 '当前来自白名单之外来源的连接数（实例配置了连接来源白名单时产出；本地连接视为可信）；'
 || '大于 0 说明存在未登记的访问来源，需核实是否为合法新应用'),
('mysql.security.unknown_sources',
 '白名单外来源明细',
 'mysql', 'security', 'analysis', 'text', NULL,
 'mysql.processlist', 'state', '1m',
 '白名单外来源明细 JSON：[{host,user,db,total}]（最多 20 条）')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. SSL 与表锁/缓存指标 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.security.ssl_enabled',
 'SSL 传输加密启用状态',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.ssl_status', 'state', '1d',
 '实例是否启用 SSL 传输加密（1=已启用 / 0=未启用）；内网环境未启用属常见选择，'
 || '按"关注"而非告警处理'),
('mysql.security.ssl_cert_days_left',
 'SSL 证书剩余有效天数',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.ssl_status', 'state', '1d',
 '服务端 SSL 证书剩余有效天数（Ssl_server_not_after 解析，5.7+ 且启用 SSL 时有值）；'
 || '到期后使用 SSL 的客户端将无法连接'),
('mysql.security.ssl_info',
 'SSL 配置详情',
 'mysql', 'security', 'analysis', 'text', NULL,
 'mysql.ssl_status', 'state', '1d',
 'SSL 配置详情 JSON：{haveSsl,tlsVersion,requireSecureTransport,certNotAfter,certDaysLeft}'),
('mysql.lock.table_in_use_count',
 '被占用表数量',
 'mysql', 'lock', 'analysis', 'numeric', 'count',
 'mysql.open_tables', 'raw', '1m',
 'SHOW OPEN TABLES 中 In_use > 0 的表数量：正被会话使用/锁定的表（MyISAM 表锁、'
 || 'LOCK TABLES、DDL），行锁监控覆盖不到的表级锁盲区（5.6 混用 MyISAM 环境重点关注）'),
('mysql.lock.table_name_locked_count',
 '名字锁定表数量',
 'mysql', 'lock', 'analysis', 'numeric', 'count',
 'mysql.open_tables', 'raw', '1m',
 '被名字锁定（RENAME/DROP 进行中）的表数量'),
('mysql.lock.open_tables_detail',
 '占用表明细',
 'mysql', 'lock', 'analysis', 'text', NULL,
 'mysql.open_tables', 'state', '1m',
 '被占用表明细 Top 20 JSON：[{db,table,inUse,nameLocked}]，按占用会话数降序'),
('mysql.rate.Table_locks_waited',
 '表锁等待速率',
 'mysql', 'lock', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒发生表锁等待的次数（Table_locks_waited 速率）；持续大于 0 说明存在表级锁竞争，'
 || '常见于 MyISAM 表或显式 LOCK TABLES'),
('mysql.rate.Table_locks_immediate',
 '表锁立即获得速率',
 'mysql', 'lock', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒立即获得表锁的次数，与 Table_locks_waited 联合计算表锁等待占比'),
('mysql.status.Open_tables',
 '表缓存中打开表数',
 'mysql', 'performance', 'analysis', 'numeric', 'count',
 'mysql.global_status', 'raw', '1m',
 '当前表缓存中打开的表数量；结合 table_open_cache 参数评估表缓存是否充足'),
('mysql.status.Open_table_definitions',
 '表定义缓存数',
 'mysql', 'performance', 'analysis', 'numeric', 'count',
 'mysql.global_status', 'raw', '1m',
 '当前缓存的表定义（.frm/数据字典）数量'),
('mysql.rate.Opened_tables',
 '表打开速率',
 'mysql', 'performance', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒新打开表的次数：持续偏高说明 table_open_cache 偏小、表缓存命中不足，'
 || '每次打开表有额外磁盘开销'),
('mysql.status.Qcache_free_memory',
 '查询缓存空闲内存',
 'mysql', 'performance', 'analysis', 'numeric', 'bytes',
 'mysql.global_status', 'raw', '1m',
 '查询缓存空闲内存字节数（仅 5.6/5.7 且启用查询缓存时产出；8.0 已移除查询缓存）'),
('mysql.status.Qcache_queries_in_cache',
 '查询缓存中语句数',
 'mysql', 'performance', 'analysis', 'numeric', 'count',
 'mysql.global_status', 'raw', '1m',
 '查询缓存中缓存的查询数量（仅 5.6/5.7）'),
('mysql.rate.Qcache_hits',
 '查询缓存命中速率',
 'mysql', 'performance', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒查询缓存命中次数（仅 5.6/5.7）；与 QPS 对比可评估查询缓存收益'),
('mysql.rate.Qcache_lowmem_prunes',
 '查询缓存淘汰速率',
 'mysql', 'performance', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒因内存不足从查询缓存淘汰的查询数（仅 5.6/5.7）；持续偏高说明 query_cache_size '
 || '不足或碎片化严重，也可能查询缓存本身已成瓶颈（考虑关闭）'),
('mysql.conn.statedetail.sending_data',
 '执行/传输数据线程数',
 'mysql', 'connection', 'analysis', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 '状态为 Sending data / executing 的活跃线程数：正在读取行并返回结果，'
 || '数量持续偏高且伴随慢 SQL 时通常是大结果集/全表扫描'),
('mysql.conn.statedetail.sorting',
 '排序中线程数',
 'mysql', 'connection', 'analysis', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 '状态含 Sorting 的线程数：正在做排序（filesort），持续偏高需检查缺失索引或 sort_buffer_size'),
('mysql.conn.statedetail.tmp_table',
 '临时表操作线程数',
 'mysql', 'connection', 'analysis', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 '状态含 tmp table / temporary 的线程数：正在创建/写入临时表'),
('mysql.conn.statedetail.copying',
 '复制表数据线程数',
 'mysql', 'connection', 'analysis', 'numeric', 'count',
 'mysql.processlist', 'raw', '1m',
 '状态含 copy 的线程数：常见于 ALTER TABLE 复制表数据阶段，此期间表会被锁定')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 4. 内置规则 ----

-- 4.1 权限变更提醒（布尔型：发生即触发）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '发生权限变更操作',
    'builtin.priv_change.notice',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    (SELECT jsonb_agg(id ORDER BY id) FROM database_version
      WHERE db_type = 'mysql' AND version_code IN ('5.7', '8.0', '8.4')),
    'mysql.security.priv_change_delta',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"检测到 GRANT/REVOKE/账号管理等权限变更语句执行时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"后续周期无权限变更语句时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '本采集周期内检测到权限变更语句（GRANT/REVOKE/CREATE USER/DROP USER/ALTER USER 等）；'
    '请核实是否为计划内变更，非计划变更需立即排查操作来源'
) ON CONFLICT (rule_code) DO NOTHING;

-- 4.2 危险操作告警（布尔型）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '发生危险操作（删表/清空/整表修改）',
    'builtin.dangerous_op.warning',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    (SELECT jsonb_agg(id ORDER BY id) FROM database_version
      WHERE db_type = 'mysql' AND version_code IN ('5.7', '8.0', '8.4')),
    'mysql.security.dangerous_op_delta',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"检测到 DROP/TRUNCATE/不带 WHERE 的 DELETE/UPDATE 执行时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"后续周期无危险语句时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '本采集周期内检测到危险语句（DROP TABLE/DROP DATABASE/TRUNCATE 或不带 WHERE 的整表 '
    'DELETE/UPDATE）；请立即核实是否为计划内操作，误操作需尽快评估数据恢复方案'
) ON CONFLICT (rule_code) DO NOTHING;

-- 4.3 暴力破解疑似（布尔型复合判定）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '疑似口令暴力破解',
    'builtin.brute_force.suspect',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.security.brute_force_suspect',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"单一来源 IP 每分钟认证失败 ≥ 5 次，或全体来源合计 ≥ 15 次时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"认证失败频次回落后自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '认证失败次数与来源集中度达到暴力破解特征（单一 IP 高频失败或整体失败激增）；'
    '请查看认证失败来源明细，确认后可在防火墙封禁来源 IP 并检查账号口令强度'
) ON CONFLICT (rule_code) DO NOTHING;

-- 4.4 白名单外来源接入（布尔型；仅配置了白名单的实例会产生该指标）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '出现白名单外连接来源',
    'builtin.unknown_source.warning',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.security.unknown_source_count',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"存在来自连接来源白名单之外的连接时触发（需先在实例管理配置白名单）"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"白名单外连接断开后自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '检测到来自白名单之外来源的数据库连接；请核实是否为合法新应用：'
    '是则将其加入实例的连接来源白名单，否则排查连接来源并考虑封禁'
) ON CONFLICT (rule_code) DO NOTHING;

-- 4.5 SSL 证书即将过期（天级扫描）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    'SSL 证书即将过期',
    'builtin.ssl.cert_expiring',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.security.ssl_cert_days_left',
    '{"operator":"<=","threshold":30.0,"unit":"count"}',
    '{"operator":">","threshold":30.0}',
    NULL,
    1440, 'SYSTEM_DEFAULT', 'system',
    '服务端 SSL 证书剩余有效期不足 30 天；到期后使用加密连接的客户端将无法连接，'
    '请提前更换证书（alter instance reload tls 或重启生效，视版本而定）'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 5. 新规则的指标关联 ----
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN (
     'builtin.priv_change.notice',
     'builtin.dangerous_op.warning',
     'builtin.brute_force.suspect',
     'builtin.unknown_source.warning',
     'builtin.ssl.cert_expiring'
 )
ON CONFLICT (rule_id, metric_code) DO NOTHING;
