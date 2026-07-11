-- =============================================================
-- V135：主机指标监控（三）——host.* 内置告警规则种子
--   db_type_id 指向 database_type(code='HOST')（V133 登记）。
--   评估侧适配：AlertEvaluateJobHandler 对 HOST 类型规则匹配
--   「host_id IS NOT NULL 的实例」（跳过版本过滤），其余生命周期逻辑复用。
--   condition_config.duration 单位为秒（墙钟时间，见 AlertConditionEvaluator）。
-- =============================================================

-- [1] 主机不可达（level_1，持续 3 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机不可达',
    'builtin.host.unreachable',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.availability',
    '{"operator":"<","threshold":1.0,"duration":180,"conditionType":"boolean",'
    '"displayText":"主机指标采集连续 3 分钟失败（exporter 不可达）时触发"}',
    '{"operator":">=","threshold":1.0,'
    '"displayText":"主机指标采集恢复正常时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '连续 3 分钟无法拉取主机指标（exporter 不可达），主机可能宕机、网络中断或 exporter 进程退出；'
    '请先确认主机与数据库实例是否仍然存活'
) ON CONFLICT (rule_code) DO NOTHING;

-- [2] CPU 持续过高（level_2，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机 CPU 持续过高',
    'builtin.host.cpu.high',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.cpu.usage',
    '{"operator":">=","threshold":90.0,"duration":300,"unit":"percent"}',
    '{"operator":"<","threshold":80.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '主机 CPU 使用率 ≥ 90% 持续 5 分钟，数据库响应可能变慢；'
    '建议结合 Top SQL 与活跃线程排查是否存在高耗 CPU 查询或主机上其他进程抢占资源'
) ON CONFLICT (rule_code) DO NOTHING;

-- [3] IO 等待过高（level_2，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机 IO 等待过高',
    'builtin.host.iowait.high',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.cpu.iowait',
    '{"operator":">=","threshold":30.0,"duration":300,"unit":"percent"}',
    '{"operator":"<","threshold":15.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    'CPU IOWait 占比 ≥ 30% 持续 5 分钟，磁盘 IO 已成为瓶颈；'
    '建议检查慢 SQL 全表扫描、脏页刷盘与磁盘健康状态'
) ON CONFLICT (rule_code) DO NOTHING;

-- [4] 系统负载过高（level_3，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机系统负载过高',
    'builtin.host.load.high',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.load.per_core',
    '{"operator":">=","threshold":2.0,"duration":300,"unit":"count"}',
    '{"operator":"<","threshold":1.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '单核平均负载（load1/核数）≥ 2 持续 5 分钟，任务排队明显；'
    '请关注 CPU、IO 与数据库活跃线程是否同步升高'
) ON CONFLICT (rule_code) DO NOTHING;

-- [5] 内存使用率过高（level_2，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机内存使用率过高',
    'builtin.host.mem.high',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.mem.usage',
    '{"operator":">=","threshold":90.0,"duration":300,"unit":"percent"}',
    '{"operator":"<","threshold":80.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '主机内存使用率 ≥ 90% 持续 5 分钟，MySQL 进程存在被系统 OOM 终止的风险；'
    '建议检查 innodb_buffer_pool_size 配置与主机上其他内存大户进程'
) ON CONFLICT (rule_code) DO NOTHING;

-- [6] Swap 使用告警（level_3，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机 Swap 使用偏高',
    'builtin.host.swap.used',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.swap.usage',
    '{"operator":">=","threshold":30.0,"duration":300,"unit":"percent"}',
    '{"operator":"<","threshold":10.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    'Swap 使用率 ≥ 30% 持续 5 分钟，内存已不足并开始换页，数据库性能会明显抖动；'
    '建议排查内存占用并评估扩容'
) ON CONFLICT (rule_code) DO NOTHING;

-- [7] 磁盘空间不足（level_1，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机磁盘空间不足',
    'builtin.host.disk.high',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.disk.usage_max',
    '{"operator":">=","threshold":85.0,"unit":"percent"}',
    '{"operator":"<","threshold":80.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '存在使用率 ≥ 85% 的磁盘挂载点，磁盘写满将导致数据库无法写入甚至崩溃；'
    '建议清理日志/binlog 或扩容，挂载点明细见实例详情主机资源页'
) ON CONFLICT (rule_code) DO NOTHING;

-- [8] 磁盘空间严重不足（level_1，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机磁盘空间严重不足',
    'builtin.host.disk.critical',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.disk.usage_max',
    '{"operator":">=","threshold":95.0,"unit":"percent"}',
    '{"operator":"<","threshold":90.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '存在使用率 ≥ 95% 的磁盘挂载点，随时可能写满，数据库面临立即不可写的风险；'
    '请立即清理空间或紧急扩容'
) ON CONFLICT (rule_code) DO NOTHING;

-- [9] Inode 不足（level_2，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机 Inode 不足',
    'builtin.host.inode.high',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.disk.inode_usage_max',
    '{"operator":">=","threshold":85.0,"unit":"percent"}',
    '{"operator":"<","threshold":75.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '存在 inode 使用率 ≥ 85% 的挂载点，inode 耗尽后即使磁盘有剩余空间也无法创建新文件；'
    '常见原因是海量小文件（如未清理的会话/临时文件）'
) ON CONFLICT (rule_code) DO NOTHING;

-- [10] 文件系统只读（level_1，布尔型，即时）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机文件系统只读',
    'builtin.host.fs.readonly',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.disk.readonly_count',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"检测到文件系统进入只读挂载状态时立即触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"全部文件系统恢复读写挂载时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '主机存在只读挂载的文件系统，通常由磁盘故障或文件系统错误引起，数据库写入会立即失败；'
    '请检查 dmesg 与磁盘健康状态，必要时联系主机管理员'
) ON CONFLICT (rule_code) DO NOTHING;

-- [11] 磁盘 IO 繁忙（level_3，持续 5 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '主机磁盘 IO 繁忙',
    'builtin.host.diskio.busy',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'HOST'),
    NULL,
    'host.diskio.util_max',
    '{"operator":">=","threshold":90.0,"duration":300,"unit":"percent"}',
    '{"operator":"<","threshold":70.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '磁盘 IO 繁忙度 ≥ 90% 持续 5 分钟，磁盘接近饱和，数据库读写延迟会明显上升；'
    '建议排查大批量写入、备份任务与慢 SQL'
) ON CONFLICT (rule_code) DO NOTHING;

-- ---- 指标关联 ----
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN (
     'builtin.host.unreachable',
     'builtin.host.cpu.high',
     'builtin.host.iowait.high',
     'builtin.host.load.high',
     'builtin.host.mem.high',
     'builtin.host.swap.used',
     'builtin.host.disk.high',
     'builtin.host.disk.critical',
     'builtin.host.inode.high',
     'builtin.host.fs.readonly',
     'builtin.host.diskio.busy'
 )
ON CONFLICT (rule_id, metric_code) DO NOTHING;
