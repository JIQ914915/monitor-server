-- SQL Server 深度诊断：事务、阻塞、文件/卷、日志、tempdb、Query Store、Agent、复制与容量报告。
INSERT INTO sys_dict_type(dict_type,dict_name,remark) VALUES
 ('capacity_prediction_status','容量预测状态','跨数据库容量预测结论'),
 ('sqlserver_agent_job_status','SQL Server Agent 作业状态','最近运行或当前运行状态'),
 ('sqlserver_plan_change_status','SQL Server 计划变化状态','Query Store 计划变化结论')
ON CONFLICT(dict_type) DO NOTHING;

INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('capacity_prediction_status','stable','增长稳定','success',1),
 ('capacity_prediction_status','risk','存在耗尽风险','danger',2),
 ('capacity_prediction_status','insufficient','历史或磁盘数据不足','info',3),
 ('sqlserver_agent_job_status','0','失败','danger',1),
 ('sqlserver_agent_job_status','1','成功','success',2),
 ('sqlserver_agent_job_status','2','重试','warning',3),
 ('sqlserver_agent_job_status','3','已取消','warning',4),
 ('sqlserver_agent_job_status','4','运行中','primary',5),
 ('sqlserver_agent_job_status','5','尚无运行记录','info',6),
 ('sqlserver_plan_change_status','0','计划未变化','success',1),
 ('sqlserver_plan_change_status','1','检测到计划变化','warning',2)
) v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d
 WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO metric_definition
(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES
 ('sqlserver.transaction.open_count','打开事务数','sqlserver','transaction','analysis','numeric','count','transactions','raw','1m','当前可见的用户活动事务数量'),
 ('sqlserver.transaction.max_seconds','最长事务时长','sqlserver','transaction','guard','numeric','seconds','transactions','raw','1m','当前用户事务最大持续秒数'),
 ('sqlserver.transaction.sleeping_open_count','睡眠未提交事务数','sqlserver','transaction','guard','numeric','count','transactions','state','1m','session 为 sleeping 且仍有打开事务的数量'),
 ('sqlserver.transaction.sleeping_open_max_seconds','最长睡眠未提交事务时长','sqlserver','transaction','guard','numeric','seconds','transactions','raw','1m','sleeping open transaction 的最大持续秒数'),
 ('sqlserver.transaction.open_seconds','会话事务时长','sqlserver','transaction','explain','numeric','seconds','transactions','raw','1m','会话对象指标'),
 ('sqlserver.transaction.sleeping_open','会话睡眠未提交状态','sqlserver','transaction','explain','numeric','count','transactions','state','1m','会话对象指标；1=睡眠且事务未提交'),
 ('sqlserver.transaction.snapshot','事务诊断快照','sqlserver','transaction','explain','text',NULL,'transactions','state','1m','会话、库、客户端与脱敏 SQL 证据'),

 ('sqlserver.blocking.max_wait_seconds','最长阻塞时长','sqlserver','lock','guard','numeric','seconds','blocking_chains','raw','1m','当前阻塞请求最大等待秒数'),
 ('sqlserver.blocking.max_chain_depth','最大阻塞链深度','sqlserver','lock','guard','numeric','count','blocking_chains','state','1m','等待会话到根阻塞者的最大边数'),
 ('sqlserver.blocking.root_blocker_count','根阻塞者数量','sqlserver','lock','analysis','numeric','count','blocking_chains','state','1m','当前不同根阻塞会话数量'),
 ('sqlserver.blocking.wait_seconds','会话阻塞时长','sqlserver','lock','explain','numeric','seconds','blocking_chains','raw','1m','等待会话对象指标'),
 ('sqlserver.blocking.chain_depth','会话阻塞链深度','sqlserver','lock','explain','numeric','count','blocking_chains','state','1m','等待会话对象指标'),
 ('sqlserver.blocking.root_affected_sessions','根阻塞影响会话数','sqlserver','lock','explain','numeric','count','blocking_chains','state','1m','根阻塞会话对象指标'),
 ('sqlserver.blocking.snapshot','阻塞链快照','sqlserver','lock','explain','text',NULL,'blocking_chains','state','1m','等待、阻塞、根会话和脱敏 SQL 证据'),

 ('sqlserver.file.size_bytes','数据库文件容量','sqlserver','capacity','analysis','numeric','bytes','file_storage','raw','1h','数据库文件对象当前分配容量'),
 ('sqlserver.file.used_bytes','数据文件已用容量','sqlserver','capacity','analysis','numeric','bytes','file_storage','raw','1h','数据文件对象已用容量；日志文件不使用 FILEPROPERTY 估算'),
 ('sqlserver.file.max_size_bytes','数据库文件最大容量','sqlserver','capacity','explain','numeric','bytes','file_storage','state','1h','有限 max_size 文件的上限'),
 ('sqlserver.file.growth_bytes','文件定长增长量','sqlserver','capacity','explain','numeric','bytes','file_storage','state','1h','非百分比自动增长配置'),
 ('sqlserver.file.growth_percent','文件百分比增长比例','sqlserver','capacity','explain','numeric','percent','file_storage','state','1h','百分比自动增长配置'),
 ('sqlserver.file.percent_growth_count','百分比增长文件数','sqlserver','capacity','guard','numeric','count','file_storage','state','1h','使用百分比自动增长的文件数量'),
 ('sqlserver.file.unlimited_growth_count','无限增长文件数','sqlserver','capacity','analysis','numeric','count','file_storage','state','1h','max_size=-1 的文件数量，需结合卷剩余空间判断'),
 ('sqlserver.file.layout_snapshot','数据库文件布局快照','sqlserver','capacity','explain','text',NULL,'file_storage','state','1h','文件类型、大小、增长方式和承载卷'),
 ('sqlserver.volume.total_bytes','文件卷总容量','sqlserver','capacity','analysis','numeric','bytes','file_storage','raw','1h','SQL Server 文件所在卷对象总容量'),
 ('sqlserver.volume.available_bytes','文件卷剩余容量','sqlserver','capacity','guard','numeric','bytes','file_storage','raw','1h','SQL Server 文件所在卷对象剩余容量'),
 ('sqlserver.volume.free_percent','文件卷剩余比例','sqlserver','capacity','guard','numeric','percent','file_storage','ratio','1h','SQL Server 文件所在卷对象剩余比例'),
 ('sqlserver.volume.min_free_percent','文件卷最小剩余比例','sqlserver','capacity','guard','numeric','percent','file_storage','ratio','1h','所有 SQL Server 文件卷中的最小剩余比例'),

 ('sqlserver.log.bytes_flushed_per_sec','日志刷新字节速率','sqlserver','log','analysis','numeric','bytes_per_sec','performance','delta','1m','Databases _Total Log Bytes Flushed/sec 相邻采样速率'),
 ('sqlserver.log.flushes_per_sec','日志刷新次数速率','sqlserver','log','analysis','numeric','count_per_sec','performance','delta','1m','Databases _Total Log Flushes/sec 相邻采样速率'),
 ('sqlserver.log.flush_latency_ms','平均日志刷新等待','sqlserver','log','guard','numeric','ms','performance','ratio','1m','相邻采样 Log Flush Wait Time 增量除以 Log Flushes 增量'),
 ('sqlserver.log.vlf_count','数据库 VLF 数量','sqlserver','log','analysis','numeric','count','log_health','state','1h','数据库对象 VLF 总数'),
 ('sqlserver.log.active_vlf_count','数据库活动 VLF 数量','sqlserver','log','analysis','numeric','count','log_health','state','1h','数据库对象活动 VLF 数量'),
 ('sqlserver.log.vlf_total_count','实例 VLF 总数','sqlserver','log','analysis','numeric','count','log_health','state','1h','所有可访问用户数据库 VLF 总数'),
 ('sqlserver.log.vlf_max_count','单库最大 VLF 数量','sqlserver','log','guard','numeric','count','log_health','state','1h','用户数据库中的最大 VLF 数量'),
 ('sqlserver.log.active_vlf_total_count','实例活动 VLF 总数','sqlserver','log','analysis','numeric','count','log_health','state','1h','所有可访问用户数据库活动 VLF 总数'),

 ('sqlserver.tempdb.data_file_count','tempdb 数据文件数','sqlserver','tempdb','analysis','numeric','count','tempdb','state','1m','tempdb ROWS 数据文件数量'),
 ('sqlserver.tempdb.percent_growth_file_count','tempdb 百分比增长文件数','sqlserver','tempdb','guard','numeric','count','tempdb','state','1m','tempdb 使用百分比增长的文件数量'),
 ('sqlserver.tempdb.data_file_size_skew_percent','tempdb 数据文件大小偏差','sqlserver','tempdb','guard','numeric','percent','tempdb','ratio','1m','最大与最小 tempdb 数据文件容量差占最大文件比例'),
 ('sqlserver.tempdb.file_size_bytes','tempdb 文件容量','sqlserver','tempdb','explain','numeric','bytes','tempdb','raw','1m','tempdb 文件对象容量'),
 ('sqlserver.tempdb.file_growth_bytes','tempdb 文件定长增长量','sqlserver','tempdb','explain','numeric','bytes','tempdb','state','1m','tempdb 文件对象定长增长配置'),
 ('sqlserver.tempdb.file_growth_percent','tempdb 文件百分比增长','sqlserver','tempdb','explain','numeric','percent','tempdb','state','1m','tempdb 文件对象百分比增长配置'),
 ('sqlserver.tempdb.volume_available_bytes','tempdb 文件卷剩余容量','sqlserver','tempdb','explain','numeric','bytes','tempdb','raw','1m','tempdb 文件所在卷对象剩余容量'),
 ('sqlserver.tempdb.pagelatch_waiting_tasks','tempdb PAGELATCH 等待任务数','sqlserver','tempdb','guard','numeric','count','tempdb','raw','1m','当前 resource_description 归属 tempdb 的 PAGELATCH 等待任务数'),
 ('sqlserver.tempdb.pagelatch_max_wait_ms','tempdb PAGELATCH 最大等待','sqlserver','tempdb','guard','numeric','ms','tempdb','raw','1m','当前 tempdb PAGELATCH 任务最大等待毫秒数'),

 ('sqlserver.query_store.enabled_database_count','Query Store 可用数据库数','sqlserver','sql','analysis','numeric','count','query_store_regression','state','1h','Query Store 实际状态非 OFF 的用户数据库数'),
 ('sqlserver.query_store.plan_changed_query_count','计划变化查询数','sqlserver','sql','guard','numeric','count','query_store_regression','state','1h','最近一小时出现新计划或多个计划的查询数'),
 ('sqlserver.query_store.max_regression_ratio','最大性能回退倍数','sqlserver','sql','guard','numeric','ratio','query_store_regression','ratio','1h','最近一小时平均耗时相对前七天同计划基线的最大倍数'),
 ('sqlserver.query_store.plan_changed','查询计划变化状态','sqlserver','sql','explain','numeric','count','query_store_regression','state','1h','查询对象指标；字典 sqlserver_plan_change_status'),
 ('sqlserver.query_store.current_plan_count','查询当前计划数','sqlserver','sql','explain','numeric','count','query_store_regression','state','1h','查询对象最近一小时出现的计划数'),
 ('sqlserver.query_store.regression_ratio','查询性能回退倍数','sqlserver','sql','explain','numeric','ratio','query_store_regression','ratio','1h','查询对象最近一小时相对历史基线的耗时倍数'),
 ('sqlserver.query_store.regression_snapshot','Query Store 回退快照','sqlserver','sql','explain','text',NULL,'query_store_regression','state','1h','数据库、查询哈希、执行次数、计划数和回退倍数'),

 ('sqlserver.agent.job_enabled','Agent 作业启用状态','sqlserver','operation','explain','numeric','count','agent_jobs','state','1h','作业对象；1=启用，0=禁用'),
 ('sqlserver.agent.job_status_code','Agent 作业状态码','sqlserver','operation','explain','numeric','count','agent_jobs','state','1h','作业对象；字典 sqlserver_agent_job_status'),
 ('sqlserver.agent.job_duration_seconds','Agent 作业最近耗时','sqlserver','operation','analysis','numeric','seconds','agent_jobs','raw','1h','作业对象最近一次运行耗时'),
 ('sqlserver.agent.job_consecutive_failures','Agent 作业连续失败次数','sqlserver','operation','guard','numeric','count','agent_jobs','state','1h','作业对象自最近成功后连续失败次数'),
 ('sqlserver.agent.job_running_seconds','Agent 作业当前运行时长','sqlserver','operation','analysis','numeric','seconds','agent_jobs','raw','1h','当前运行作业对象持续秒数'),
 ('sqlserver.agent.consecutive_failure_jobs','连续失败作业数','sqlserver','operation','guard','numeric','count','agent_jobs','state','1h','连续失败次数大于零的作业数量'),
 ('sqlserver.agent.max_running_seconds','作业最大运行时长','sqlserver','operation','analysis','numeric','seconds','agent_jobs','raw','1h','当前运行作业的最大持续秒数'),
 ('sqlserver.agent.job_snapshot','Agent 作业状态快照','sqlserver','operation','explain','text',NULL,'agent_jobs','state','1h','作业启停、状态、最近/下次运行与失败步骤，不采集作业命令'),

 ('sqlserver.replication.delivery_latency_ms','复制代理投递延迟','sqlserver','replication','explain','numeric','ms','replication_cdc','raw','1h','Replication Dist/Logreader 性能计数器对象延迟'),
 ('sqlserver.replication.max_delivery_latency_ms','复制最大投递延迟','sqlserver','replication','guard','numeric','ms','replication_cdc','raw','1h','可见复制代理性能计数器中的最大投递延迟'),
 ('sqlserver.cdc.scan_latency_seconds','CDC 日志扫描延迟','sqlserver','replication','explain','numeric','seconds','replication_cdc','raw','1h','CDC 数据库最大已捕获 LSN 时间距当前时间'),
 ('sqlserver.cdc.capture_instance_count','CDC 捕获实例数','sqlserver','replication','analysis','numeric','count','replication_cdc','state','1h','所有启用 CDC 数据库的 capture instance 数量'),
 ('sqlserver.cdc.max_scan_latency_seconds','CDC 最大日志扫描延迟','sqlserver','replication','guard','numeric','seconds','replication_cdc','raw','1h','启用 CDC 数据库中的最大扫描延迟')
ON CONFLICT(metric_code) DO UPDATE SET
 metric_name=EXCLUDED.metric_name,domain=EXCLUDED.domain,layer=EXCLUDED.layer,
 value_type=EXCLUDED.value_type,unit=EXCLUDED.unit,source_collector=EXCLUDED.source_collector,
 process_type=EXCLUDED.process_type,frequency=EXCLUDED.frequency,description=EXCLUDED.description;

INSERT INTO alert_rule
(rule_name,rule_code,rule_level,db_type_id,db_version_ids,metric_name,condition_config,recovery_config,
 notification_config,scan_interval_min,scan_interval_source,created_by,description,recommended)
VALUES
 ('SQL Server 长事务持续','builtin.sqlserver.transaction.long','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.transaction.max_seconds','{"operator":">","threshold":300,"duration":5,"unit":"seconds"}','{"operator":"<=","threshold":300}',NULL,1,'SYSTEM_DEFAULT','system','事务持续时间超过预设阈值；请结合会话、客户端和脱敏 SQL 人工确认，不自动终止事务',TRUE),
 ('SQL Server 睡眠会话事务未提交','builtin.sqlserver.transaction.sleeping_open','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.transaction.sleeping_open_max_seconds','{"operator":">","threshold":300,"duration":5,"unit":"seconds"}','{"operator":"<=","threshold":300}',NULL,1,'SYSTEM_DEFAULT','system','sleeping 会话仍持有未提交事务；检查应用提交/回滚路径和连接池使用',TRUE),
 ('SQL Server 持续阻塞','builtin.sqlserver.blocking.duration','level_1',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.blocking.max_wait_seconds','{"operator":">","threshold":60,"duration":1,"unit":"seconds"}','{"operator":"<=","threshold":60}',NULL,1,'SYSTEM_DEFAULT','system','阻塞超过预设时长；从根阻塞者、链深和事务开始时间人工定位，不自动 KILL 会话',TRUE),
 ('SQL Server 文件卷空间不足','builtin.sqlserver.volume.free_percent','level_1',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.volume.min_free_percent','{"operator":"<","threshold":15,"duration":0,"unit":"percent"}','{"operator":">=","threshold":20}',NULL,60,'SYSTEM_DEFAULT','system','SQL Server 数据、日志或 tempdb 文件所在卷剩余比例不足；评估增长、归档或人工扩容',TRUE),
 ('SQL Server 日志刷新延迟','builtin.sqlserver.log.flush_latency','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.log.flush_latency_ms','{"operator":">","threshold":20,"duration":5,"unit":"ms"}','{"operator":"<=","threshold":20}',NULL,1,'SYSTEM_DEFAULT','system','日志 flush 平均等待持续偏高；关联 WRITELOG、日志盘 I/O、事务提交频率和同步副本',TRUE),
 ('SQL Server VLF 数量过多','builtin.sqlserver.log.vlf_count','level_3',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.log.vlf_max_count','{"operator":">","threshold":1000,"duration":0,"unit":"count"}','{"operator":"<=","threshold":1000}',NULL,60,'SYSTEM_DEFAULT','system','单库 VLF 数量超过预设阈值；结合日志增长历史人工调整增长策略，不自动收缩日志',FALSE),
 ('SQL Server tempdb 分配争用','builtin.sqlserver.tempdb.pagelatch','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.tempdb.pagelatch_waiting_tasks','{"operator":">","threshold":0,"duration":5,"unit":"count"}','{"operator":"<=","threshold":0}',NULL,1,'SYSTEM_DEFAULT','system','持续存在 tempdb PAGELATCH 等待；结合文件数量、大小偏差和增长方式人工评估',TRUE),
 ('SQL Server Query Store 性能回退','builtin.sqlserver.query_store.regression','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.query_store.max_regression_ratio','{"operator":">","threshold":2,"duration":0,"unit":"ratio"}','{"operator":"<=","threshold":1.5}',NULL,60,'SYSTEM_DEFAULT','system','最近一小时查询平均耗时明显高于历史基线；对比计划变化和等待，不自动强制计划',TRUE),
 ('SQL Server Agent 作业连续失败','builtin.sqlserver.agent.consecutive_failure','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.agent.consecutive_failure_jobs','{"operator":">","threshold":0,"duration":0,"unit":"count"}','{"operator":"<=","threshold":0}',NULL,60,'SYSTEM_DEFAULT','system','存在自最近成功后连续失败的 Agent 作业；查看失败步骤并人工处理，不自动重跑',TRUE),
 ('SQL Server 复制投递延迟','builtin.sqlserver.replication.delivery_latency','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.replication.max_delivery_latency_ms','{"operator":">","threshold":60000,"duration":5,"unit":"ms"}','{"operator":"<=","threshold":30000}',NULL,60,'SYSTEM_DEFAULT','system','复制代理投递延迟持续升高；结合 Agent 作业、网络和订阅端处理能力排查',FALSE),
 ('SQL Server CDC 扫描延迟','builtin.sqlserver.cdc.scan_latency','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.cdc.max_scan_latency_seconds','{"operator":">","threshold":300,"duration":5,"unit":"seconds"}','{"operator":"<=","threshold":120}',NULL,60,'SYSTEM_DEFAULT','system','CDC 最大捕获 LSN 长时间落后；结合 capture job、日志复用等待和事务情况排查',FALSE)
ON CONFLICT(rule_code) DO UPDATE SET
 rule_name=EXCLUDED.rule_name,rule_level=EXCLUDED.rule_level,metric_name=EXCLUDED.metric_name,
 condition_config=EXCLUDED.condition_config,recovery_config=EXCLUDED.recovery_config,
 description=EXCLUDED.description,recommended=EXCLUDED.recommended;