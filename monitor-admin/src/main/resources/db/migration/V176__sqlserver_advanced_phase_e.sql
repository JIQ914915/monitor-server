-- SQL Server 批次 E：Agent、日志传送、复制/CDC、配置漂移和索引候选。
INSERT INTO sys_dict_type(dict_type,dict_name,remark) VALUES
 ('sqlserver_job_status','SQL Server Agent 作业状态','作业状态统一字典'),
 ('sqlserver_log_shipping_status','SQL Server 日志传送状态','备份、复制、还原阶段状态'),
 ('sqlserver_candidate_status','SQL Server 候选建议状态','仅建议人工验证，不自动执行')
ON CONFLICT(dict_type) DO NOTHING;
INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('sqlserver_job_status','succeeded','成功','success',1),('sqlserver_job_status','failed','失败','danger',2),('sqlserver_job_status','running','运行中','warning',3),('sqlserver_job_status','disabled','已停用','info',4),('sqlserver_job_status','unsupported','不支持','info',5),
 ('sqlserver_log_shipping_status','normal','正常','success',1),('sqlserver_log_shipping_status','backup_delayed','备份延迟','danger',2),('sqlserver_log_shipping_status','copy_delayed','复制延迟','danger',3),('sqlserver_log_shipping_status','restore_delayed','还原延迟','danger',4),('sqlserver_log_shipping_status','stale','监控数据过期','warning',5),
 ('sqlserver_candidate_status','observing','继续观察','info',1),('sqlserver_candidate_status','review','建议人工评审','warning',2),('sqlserver_candidate_status','rejected','已排除','info',3),('sqlserver_candidate_status','accepted','已采纳','success',4)
)v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO metric_definition(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES
 ('sqlserver.agent.job_count','Agent 作业数','sqlserver','job','analysis','numeric','count','agent','raw','1h','不采集敏感作业命令文本'),
 ('sqlserver.agent.disabled_jobs','停用作业数','sqlserver','job','analysis','numeric','count','agent','raw','1h','已停用作业数'),
 ('sqlserver.agent.failed_jobs','最近失败作业数','sqlserver','job','guard','numeric','count','agent','raw','1h','最近一次运行失败的作业数'),
 ('sqlserver.agent.running_jobs','运行中作业数','sqlserver','job','analysis','numeric','count','agent','raw','1h','当前运行中作业数'),
 ('sqlserver.log_shipping.backup_delay_minutes','日志传送备份延迟','sqlserver','log_shipping','guard','numeric','count','log_shipping','raw','1h','Primary 最近备份延迟'),
 ('sqlserver.log_shipping.copy_delay_minutes','日志传送复制延迟','sqlserver','log_shipping','guard','numeric','count','log_shipping','raw','1h','Secondary 最近复制延迟'),
 ('sqlserver.log_shipping.restore_delay_minutes','日志传送还原延迟','sqlserver','log_shipping','guard','numeric','count','log_shipping','raw','1h','Secondary 最近还原延迟'),
 ('sqlserver.replication.published_databases','复制发布数据库数','sqlserver','replication','analysis','numeric','count','replication_cdc','state','1d','发布或合并发布数据库数'),
 ('sqlserver.replication.subscribed_databases','复制订阅数据库数','sqlserver','replication','analysis','numeric','count','replication_cdc','state','1d','订阅数据库数'),
 ('sqlserver.cdc.enabled_databases','CDC 启用数据库数','sqlserver','cdc','analysis','numeric','count','replication_cdc','state','1d','启用 CDC 数据库数'),
 ('sqlserver.configuration.snapshot','关键配置快照','sqlserver','configuration','explain','text',NULL,'configuration','state','1d','相同内容按哈希覆盖去重，不在采集时访问外网'),
 ('sqlserver.configuration.auto_shrink_databases','启用自动收缩数据库数','sqlserver','configuration','guard','numeric','count','configuration','raw','1d','auto shrink 常见风险'),
 ('sqlserver.index.missing_candidate_count','Missing Index 候选数','sqlserver','index','analysis','numeric','count','index_candidates','raw','1d','只读候选，不自动创建'),
 ('sqlserver.index.unused_candidate_count','未使用索引候选数','sqlserver','index','analysis','numeric','count','index_candidates','raw','1d','观察窗自实例启动，不自动删除'),
 ('sqlserver.index.observation_notice','索引观察窗说明','sqlserver','index','explain','text',NULL,'index_candidates','state','1d','短期未使用不代表可删除')
ON CONFLICT(metric_code) DO NOTHING;

INSERT INTO alert_rule(rule_name,rule_code,rule_level,db_type_id,metric_name,condition_config,recovery_config,scan_interval_min,scan_interval_source,created_by,description,recommended)
VALUES
 ('SQL Server Agent 作业失败','builtin.sqlserver.agent.failed','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.agent.failed_jobs','{"operator":">","threshold":0,"duration":0,"unit":"count"}','{"operator":"<=","threshold":0}',5,'SYSTEM_DEFAULT','system','作业最近一次执行失败；平台不自动重跑、停止或修改作业',TRUE),
 ('SQL Server 日志传送还原延迟','builtin.sqlserver.log_shipping.restore_delay','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.log_shipping.restore_delay_minutes','{"operator":">","threshold":30,"duration":300,"unit":"minute"}','{"operator":"<=","threshold":15}',5,'SYSTEM_DEFAULT','system','仅对已探测到日志传送的实例告警，并与 Always On 状态分开展示',TRUE),
 ('SQL Server 数据库启用自动收缩','builtin.sqlserver.configuration.auto_shrink','level_3',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.configuration.auto_shrink_databases','{"operator":">","threshold":0,"duration":0,"unit":"count"}','{"operator":"<=","threshold":0}',60,'SYSTEM_DEFAULT','system','自动收缩可能造成碎片和 I/O 抖动；仅建议人工评估修改',TRUE)
ON CONFLICT(rule_code) DO NOTHING;
