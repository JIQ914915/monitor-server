-- SQL Server 批次 B：核心健康指标、状态字典与内置告警模板。
INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('sqlserver_database_state', 'SQL Server 数据库状态', '数据库状态统一展示字典')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('sqlserver_database_state', 'ONLINE', '正常', 'success', 1),
  ('sqlserver_database_state', 'RESTORING', '正在还原', 'warning', 2),
  ('sqlserver_database_state', 'RECOVERING', '正在恢复', 'warning', 3),
  ('sqlserver_database_state', 'RECOVERY_PENDING', '等待恢复', 'danger', 4),
  ('sqlserver_database_state', 'SUSPECT', '可疑', 'danger', 5),
  ('sqlserver_database_state', 'EMERGENCY', '紧急模式', 'danger', 6),
  ('sqlserver_database_state', 'OFFLINE', '离线', 'info', 7)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
   WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value
);

INSERT INTO metric_definition
(metric_code, metric_name, db_type, domain, layer, value_type, unit,
 source_collector, process_type, frequency, description)
VALUES
('sqlserver.conn.user','用户连接数','sqlserver','connection','guard','numeric','count','performance','raw','1m','当前用户连接数'),
('sqlserver.memory.grants_pending','等待内存授权请求','sqlserver','memory','guard','numeric','count','performance','raw','1m','等待查询内存授权的请求数'),
('sqlserver.memory.grants_outstanding','已授予查询内存','sqlserver','memory','analysis','numeric','count','performance','raw','1m','当前已授予查询内存请求数'),
('sqlserver.memory.total_bytes','SQL Server 已用内存','sqlserver','memory','analysis','numeric','bytes','performance','raw','1m','Total Server Memory'),
('sqlserver.memory.target_bytes','SQL Server 目标内存','sqlserver','memory','analysis','numeric','bytes','performance','raw','1m','Target Server Memory'),
('sqlserver.buffer.ple_seconds','页面预期寿命','sqlserver','memory','analysis','numeric','count','performance','raw','1m','Page Life Expectancy，仅作为趋势证据'),
('sqlserver.batch_requests_per_sec','批处理请求速率','sqlserver','traffic','guard','numeric','qps','performance','delta','1m','Batch Requests 每秒差值'),
('sqlserver.compilations_per_sec','SQL 编译速率','sqlserver','performance','analysis','numeric','qps','performance','delta','1m','SQL Compilations 每秒差值'),
('sqlserver.recompilations_per_sec','SQL 重编译速率','sqlserver','performance','analysis','numeric','qps','performance','delta','1m','SQL Re-Compilations 每秒差值'),
('sqlserver.lazy_writes_per_sec','Lazy Write 速率','sqlserver','memory','analysis','numeric','qps','performance','delta','1m','Lazy Writes 每秒差值'),
('sqlserver.page_reads_per_sec','页面读取速率','sqlserver','io','analysis','numeric','qps','performance','delta','1m','Page Reads 每秒差值'),
('sqlserver.page_writes_per_sec','页面写入速率','sqlserver','io','analysis','numeric','qps','performance','delta','1m','Page Writes 每秒差值'),
('sqlserver.deadlocks_per_sec','死锁发生速率','sqlserver','lock','guard','numeric','qps','performance','delta','1m','Number of Deadlocks 每秒差值'),
('sqlserver.scheduler.runnable_tasks','等待 CPU 调度任务','sqlserver','cpu','guard','numeric','count','runtime','raw','1m','可见调度器 runnable_tasks 总数'),
('sqlserver.scheduler.active_workers','活跃 Worker','sqlserver','cpu','analysis','numeric','count','runtime','raw','1m','可见调度器 active_workers 总数'),
('sqlserver.scheduler.current_tasks','当前任务数','sqlserver','cpu','analysis','numeric','count','runtime','raw','1m','可见调度器 current_tasks 总数'),
('sqlserver.session.user','用户会话数','sqlserver','connection','analysis','numeric','count','runtime','raw','1m','is_user_process 用户会话数'),
('sqlserver.request.active','活跃请求数','sqlserver','connection','analysis','numeric','count','runtime','raw','1m','当前活跃请求数'),
('sqlserver.blocked_sessions','被阻塞会话数','sqlserver','lock','guard','numeric','count','runtime','raw','1m','blocking_session_id 大于零的请求数'),
('sqlserver.request.max_seconds','最长请求时长','sqlserver','performance','guard','numeric','count','runtime','raw','1m','当前最长请求运行秒数'),
('sqlserver.transaction.max_open_count','单会话最大打开事务数','sqlserver','transaction','guard','numeric','count','runtime','raw','1m','用户会话中最大 open_transaction_count'),
('sqlserver.storage.data_size_bytes','数据文件容量','sqlserver','capacity','analysis','numeric','bytes','storage','raw','1m','当前连接库数据文件总容量'),
('sqlserver.storage.data_used_bytes','数据文件已用容量','sqlserver','capacity','analysis','numeric','bytes','storage','raw','1m','当前连接库数据文件已用容量'),
('sqlserver.storage.log_size_bytes','事务日志容量','sqlserver','capacity','analysis','numeric','bytes','storage','raw','1m','当前连接库事务日志容量'),
('sqlserver.storage.log_used_percent','事务日志使用率','sqlserver','capacity','guard','numeric','percent','storage','raw','1m','当前连接库事务日志使用率'),
('sqlserver.storage.log_reuse_blocked','日志复用受阻','sqlserver','capacity','guard','numeric','count','storage','state','1m','log_reuse_wait_desc 非 NOTHING 时为 1'),
('sqlserver.io.reads_per_sec','文件读取 IOPS','sqlserver','io','analysis','numeric','qps','storage','delta','1m','文件读取次数差值速率'),
('sqlserver.io.writes_per_sec','文件写入 IOPS','sqlserver','io','analysis','numeric','qps','storage','delta','1m','文件写入次数差值速率'),
('sqlserver.io.read_latency_ms','平均读取延迟','sqlserver','io','guard','numeric','ms','storage','ratio','1m','采样区间 read stall/read 次数'),
('sqlserver.io.write_latency_ms','平均写入延迟','sqlserver','io','guard','numeric','ms','storage','ratio','1m','采样区间 write stall/write 次数'),
('sqlserver.tempdb.user_bytes','tempdb 用户对象容量','sqlserver','tempdb','analysis','numeric','bytes','tempdb','raw','1m','用户对象占用'),
('sqlserver.tempdb.internal_bytes','tempdb 内部对象容量','sqlserver','tempdb','analysis','numeric','bytes','tempdb','raw','1m','内部对象占用'),
('sqlserver.tempdb.version_store_bytes','tempdb 版本存储容量','sqlserver','tempdb','guard','numeric','bytes','tempdb','raw','1m','版本存储占用'),
('sqlserver.tempdb.free_bytes','tempdb 可用容量','sqlserver','tempdb','guard','numeric','bytes','tempdb','raw','1m','未分配空间')
ON CONFLICT (metric_code) DO NOTHING;

INSERT INTO metric_definition
(metric_code, metric_name, db_type, domain, layer, value_type, unit,
 source_collector, process_type, frequency, description)
SELECT 'sqlserver.wait.' || c.category || '.' || c.suffix,
       c.label, 'sqlserver', 'wait', 'analysis', 'numeric', c.unit,
       'wait_stats', 'delta', '1m', c.description
FROM (VALUES
 ('cpu','ms_per_sec','CPU 等待耗时速率','ms','窗口内 CPU 等待毫秒/秒'),
 ('io','ms_per_sec','I/O 等待耗时速率','ms','窗口内 I/O 等待毫秒/秒'),
 ('lock','ms_per_sec','锁等待耗时速率','ms','窗口内锁等待毫秒/秒'),
 ('log','ms_per_sec','日志等待耗时速率','ms','窗口内日志等待毫秒/秒'),
 ('memory','ms_per_sec','内存等待耗时速率','ms','窗口内内存等待毫秒/秒'),
 ('network','ms_per_sec','网络等待耗时速率','ms','窗口内网络等待毫秒/秒'),
 ('parallel','ms_per_sec','并行等待耗时速率','ms','窗口内并行等待毫秒/秒'),
 ('ha','ms_per_sec','高可用等待耗时速率','ms','窗口内高可用等待毫秒/秒'),
 ('other','ms_per_sec','其他等待耗时速率','ms','窗口内其他等待毫秒/秒'),
 ('cpu','tasks_per_sec','CPU 等待任务速率','qps','窗口内 CPU 等待任务/秒'),
 ('io','tasks_per_sec','I/O 等待任务速率','qps','窗口内 I/O 等待任务/秒'),
 ('lock','tasks_per_sec','锁等待任务速率','qps','窗口内锁等待任务/秒'),
 ('log','tasks_per_sec','日志等待任务速率','qps','窗口内日志等待任务/秒'),
 ('memory','tasks_per_sec','内存等待任务速率','qps','窗口内内存等待任务/秒'),
 ('network','tasks_per_sec','网络等待任务速率','qps','窗口内网络等待任务/秒'),
 ('parallel','tasks_per_sec','并行等待任务速率','qps','窗口内并行等待任务/秒'),
 ('ha','tasks_per_sec','高可用等待任务速率','qps','窗口内高可用等待任务/秒'),
 ('other','tasks_per_sec','其他等待任务速率','qps','窗口内其他等待任务/秒'),
 ('cpu','signal_ms_per_sec','CPU Signal Wait 速率','ms','窗口内 CPU signal wait 毫秒/秒'),
 ('io','signal_ms_per_sec','I/O Signal Wait 速率','ms','窗口内 I/O signal wait 毫秒/秒'),
 ('lock','signal_ms_per_sec','锁 Signal Wait 速率','ms','窗口内锁 signal wait 毫秒/秒'),
 ('log','signal_ms_per_sec','日志 Signal Wait 速率','ms','窗口内日志 signal wait 毫秒/秒'),
 ('memory','signal_ms_per_sec','内存 Signal Wait 速率','ms','窗口内内存 signal wait 毫秒/秒'),
 ('network','signal_ms_per_sec','网络 Signal Wait 速率','ms','窗口内网络 signal wait 毫秒/秒'),
 ('parallel','signal_ms_per_sec','并行 Signal Wait 速率','ms','窗口内并行 signal wait 毫秒/秒'),
 ('ha','signal_ms_per_sec','高可用 Signal Wait 速率','ms','窗口内高可用 signal wait 毫秒/秒'),
 ('other','signal_ms_per_sec','其他 Signal Wait 速率','ms','窗口内其他 signal wait 毫秒/秒')
) AS c(category,suffix,label,unit,description)
ON CONFLICT (metric_code) DO NOTHING;

INSERT INTO alert_rule
(rule_name, rule_code, rule_level, db_type_id, db_version_ids, metric_name,
 condition_config, recovery_config, notification_config, scan_interval_min,
 scan_interval_source, created_by, description, recommended)
VALUES
('SQL Server 实例连接失败','builtin.sqlserver.availability','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.availability',
 '{"operator":"<","threshold":1.0,"duration":180,"conditionType":"boolean"}',
 '{"operator":">=","threshold":1.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '实例连续 3 分钟无法连接；请检查数据库服务、网络、防火墙和采集账号',TRUE),
('SQL Server CPU 调度排队','builtin.sqlserver.scheduler.runnable','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.scheduler.runnable_tasks',
 '{"operator":">=","threshold":4.0,"duration":300,"unit":"count"}',
 '{"operator":"<","threshold":2.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '可见调度器持续存在 runnable 任务；请结合主机 CPU、Top SQL 和 CPU 等待判断',TRUE),
('SQL Server 查询内存授权等待','builtin.sqlserver.memory.grants_pending','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.memory.grants_pending',
 '{"operator":">=","threshold":1.0,"duration":300,"unit":"count"}',
 '{"operator":"<","threshold":1.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '查询持续等待内存授权；请检查高内存 SQL、并发与 max server memory',TRUE),
('SQL Server 持续阻塞','builtin.sqlserver.blocked_sessions','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.blocked_sessions',
 '{"operator":">=","threshold":1.0,"duration":300,"unit":"count"}',
 '{"operator":"<","threshold":1.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '存在持续阻塞会话；请下钻阻塞链并由 DBA 人工评估处置',TRUE),
('SQL Server 事务日志空间紧张','builtin.sqlserver.log_usage','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.storage.log_used_percent',
 '{"operator":">=","threshold":85.0,"duration":300,"unit":"percent"}',
 '{"operator":"<","threshold":75.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '事务日志使用率持续偏高；需结合日志复用等待、日志备份和长事务判断原因',TRUE),
('SQL Server 文件写入延迟','builtin.sqlserver.io.write_latency','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.io.write_latency_ms',
 '{"operator":">=","threshold":20.0,"duration":300,"unit":"ms"}',
 '{"operator":"<","threshold":15.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '数据库文件平均写入延迟持续偏高；请关联主机磁盘与 WRITELOG/PAGEIOLATCH 等待',TRUE)
ON CONFLICT (rule_code) DO NOTHING;
