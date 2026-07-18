-- SQL Server 核心采集正确性：状态字典、数据库健康、全库容量与损坏页线索。
INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('capability_status','permission_denied','权限不足','danger',7),
 ('capability_status','edition_not_support','Edition 不支持','info',8),
 ('capability_status','not_enabled','未启用','info',9)
) v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d
 WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO sys_dict_type(dict_type,dict_name,remark) VALUES
 ('sqlserver_database_state','SQL Server 数据库状态','sys.databases.state/state_desc'),
 ('sqlserver_database_user_access','SQL Server 数据库访问模式','sys.databases.user_access/user_access_desc'),
 ('sqlserver_recovery_model','SQL Server 恢复模式','sys.databases.recovery_model/recovery_model_desc'),
 ('sqlserver_log_reuse_wait','SQL Server 日志复用等待','sys.databases.log_reuse_wait_desc'),
 ('sqlserver_wait_category','SQL Server 等待分类','等待类型归一分类')
ON CONFLICT(dict_type) DO NOTHING;

INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('sqlserver_database_state','0','在线','success',1),
 ('sqlserver_database_state','1','正在还原','warning',2),
 ('sqlserver_database_state','2','正在恢复','warning',3),
 ('sqlserver_database_state','3','等待恢复','danger',4),
 ('sqlserver_database_state','4','可疑','danger',5),
 ('sqlserver_database_state','5','紧急模式','danger',6),
 ('sqlserver_database_state','6','离线','danger',7),
 ('sqlserver_database_state','7','正在复制','info',8),
 ('sqlserver_database_state','10','离线辅助副本','warning',9),
 ('sqlserver_database_user_access','0','多用户','success',1),
 ('sqlserver_database_user_access','1','单用户','warning',2),
 ('sqlserver_database_user_access','2','受限用户','warning',3),
 ('sqlserver_recovery_model','1','完整恢复','success',1),
 ('sqlserver_recovery_model','2','大容量日志恢复','warning',2),
 ('sqlserver_recovery_model','3','简单恢复','info',3),
 ('sqlserver_log_reuse_wait','NOTHING','可正常复用','success',1),
 ('sqlserver_log_reuse_wait','CHECKPOINT','等待检查点','warning',2),
 ('sqlserver_log_reuse_wait','LOG_BACKUP','等待日志备份','warning',3),
 ('sqlserver_log_reuse_wait','ACTIVE_BACKUP_OR_RESTORE','备份或还原进行中','warning',4),
 ('sqlserver_log_reuse_wait','ACTIVE_TRANSACTION','存在活动事务','danger',5),
 ('sqlserver_log_reuse_wait','DATABASE_MIRRORING','数据库镜像','warning',6),
 ('sqlserver_log_reuse_wait','REPLICATION','复制未完成','warning',7),
 ('sqlserver_log_reuse_wait','DATABASE_SNAPSHOT_CREATION','正在创建数据库快照','warning',8),
 ('sqlserver_log_reuse_wait','LOG_SCAN','日志扫描','warning',9),
 ('sqlserver_log_reuse_wait','AVAILABILITY_REPLICA','可用性副本未完成','warning',10),
 ('sqlserver_log_reuse_wait','OLDEST_PAGE','等待最旧页面','warning',11),
 ('sqlserver_log_reuse_wait','XTP_CHECKPOINT','等待内存优化检查点','warning',12),
 ('sqlserver_log_reuse_wait','SLOG_SCAN','等待 SLOG 扫描','warning',13),
 ('sqlserver_log_reuse_wait','OTHER_TRANSIENT','其他临时原因','info',14),
 ('sqlserver_wait_category','cpu','CPU 调度','danger',1),
 ('sqlserver_wait_category','io','数据文件 I/O','warning',2),
 ('sqlserver_wait_category','lock','锁等待','danger',3),
 ('sqlserver_wait_category','log','事务日志','warning',4),
 ('sqlserver_wait_category','memory','查询内存','warning',5),
 ('sqlserver_wait_category','network','网络输出','info',6),
 ('sqlserver_wait_category','parallel','并行执行','info',7),
 ('sqlserver_wait_category','ha','高可用','warning',8),
 ('sqlserver_wait_category','other','其他等待','info',9)
) v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d
 WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('sqlserver_query_store_collect_state','version_not_support','版本不支持，已使用 DMV','info',4),
 ('sqlserver_query_store_collect_state','not_enabled','未启用，已使用 DMV','warning',5),
 ('sqlserver_query_store_collect_state','collect_error','采集异常，已使用 DMV','danger',6)
) v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d
 WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO metric_definition
(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES
 ('sqlserver.database.total_count','用户数据库数量','sqlserver','availability','analysis','numeric','count','database_health','raw','1m','可见且非快照的用户数据库数量'),
 ('sqlserver.database.abnormal_count','状态异常数据库数','sqlserver','availability','guard','numeric','count','database_health','state','1m','RECOVERY_PENDING、SUSPECT、EMERGENCY 或 OFFLINE 用户数据库数量'),
 ('sqlserver.database.offline_count','离线数据库数','sqlserver','availability','guard','numeric','count','database_health','state','1m','OFFLINE 用户数据库数量'),
 ('sqlserver.database.suspect_count','可疑数据库数','sqlserver','availability','guard','numeric','count','database_health','state','1m','SUSPECT 用户数据库数量'),
 ('sqlserver.database.recovery_pending_count','等待恢复数据库数','sqlserver','availability','guard','numeric','count','database_health','state','1m','RECOVERY_PENDING 用户数据库数量'),
 ('sqlserver.database.emergency_count','紧急模式数据库数','sqlserver','availability','guard','numeric','count','database_health','state','1m','EMERGENCY 用户数据库数量'),
 ('sqlserver.database.read_only_count','只读数据库数','sqlserver','availability','analysis','numeric','count','database_health','state','1m','只读用户数据库数量，仅作为状态证据'),
 ('sqlserver.database.state_code','数据库状态码','sqlserver','availability','explain','numeric','count','database_health','state','1m','对象指标；字典 sqlserver_database_state'),
 ('sqlserver.database.user_access_code','数据库访问模式码','sqlserver','availability','explain','numeric','count','database_health','state','1m','对象指标；字典 sqlserver_database_user_access'),
 ('sqlserver.database.recovery_model_code','数据库恢复模式码','sqlserver','backup','explain','numeric','count','database_health','state','1m','对象指标；字典 sqlserver_recovery_model'),
 ('sqlserver.database.read_only','数据库只读状态','sqlserver','availability','explain','numeric','count','database_health','state','1m','对象指标；0=可写，1=只读'),
 ('sqlserver.database.state_snapshot','数据库状态快照','sqlserver','availability','explain','text',NULL,'database_health','state','1m','数据库名、状态、访问模式、恢复模式和只读状态；按哈希去重'),
 ('sqlserver.integrity.suspect_page_count','疑似损坏页数量','sqlserver','integrity','guard','numeric','count','integrity_events','raw','1m','msdb suspect_pages 中未修复的 823、824、校验和或页撕裂错误页面数量'),
 ('sqlserver.integrity.suspect_page_error_count','疑似损坏页错误累计','sqlserver','integrity','analysis','numeric','count','integrity_events','raw','1m','未修复 I/O 一致性错误页面的 error_count 汇总及数据库对象明细'),
 ('sqlserver.integrity.suspect_page_new_count','新增疑似损坏页错误','sqlserver','integrity','guard','numeric','count','integrity_events','delta','1m','相邻采样 error_count 增量，首采和计数回退不写零'),
 ('sqlserver.wait.type.ms_per_sec','原始等待类型耗时速率','sqlserver','wait','analysis','numeric','ms','wait_stats','delta','1m','对象指标；窗口内 Top20 原始 wait_type 等待耗时速率'),
 ('sqlserver.storage.log_reuse_wait_snapshot','日志复用等待状态快照','sqlserver','capacity','explain','text',NULL,'storage','state','1m','所有可访问用户库的 log_reuse_wait_desc 原始状态'),
 ('sqlserver.ag.is_local','AG 副本是否本地','sqlserver','ha','explain','numeric','count','always_on','state','1m','对象指标；1=本地副本，0=远程副本')
ON CONFLICT(metric_code) DO UPDATE SET
 metric_name=EXCLUDED.metric_name,domain=EXCLUDED.domain,layer=EXCLUDED.layer,
 value_type=EXCLUDED.value_type,unit=EXCLUDED.unit,source_collector=EXCLUDED.source_collector,
 process_type=EXCLUDED.process_type,frequency=EXCLUDED.frequency,description=EXCLUDED.description;

UPDATE metric_definition
   SET description='所有可访问用户数据库中日志复用受阻的数据库数量；原始原因见 sqlserver.storage.log_reuse_wait_snapshot'
 WHERE metric_code='sqlserver.storage.log_reuse_blocked';
UPDATE metric_definition
   SET description='所有可访问用户数据库数据文件总容量；数据库级明细写入对象指标'
 WHERE metric_code='sqlserver.storage.data_size_bytes';
UPDATE metric_definition
   SET description='所有可访问用户数据库数据文件已用总量；数据库级明细写入对象指标'
 WHERE metric_code='sqlserver.storage.data_used_bytes';
UPDATE metric_definition
   SET description='所有可访问用户数据库事务日志总容量；数据库级明细写入对象指标'
 WHERE metric_code='sqlserver.storage.log_size_bytes';
UPDATE metric_definition
   SET description='所有可访问用户数据库中的最大事务日志使用率；数据库级明细写入对象指标'
 WHERE metric_code='sqlserver.storage.log_used_percent';

INSERT INTO alert_rule
(rule_name,rule_code,rule_level,db_type_id,db_version_ids,metric_name,condition_config,recovery_config,
 notification_config,scan_interval_min,scan_interval_source,created_by,description,recommended)
VALUES
 ('SQL Server 用户数据库状态异常','builtin.sqlserver.database.abnormal','level_1',
  (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.database.abnormal_count',
  '{"operator":">","threshold":0,"duration":0,"unit":"count"}',
  '{"operator":"<=","threshold":0}',NULL,1,'SYSTEM_DEFAULT','system',
  '存在等待恢复、可疑、紧急模式或离线用户数据库；请按状态、影响对象和恢复模式人工排查，不自动执行恢复操作',TRUE),
 ('SQL Server 发现疑似损坏页','builtin.sqlserver.integrity.suspect_page','level_1',
  (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.integrity.suspect_page_count',
  '{"operator":">","threshold":0,"duration":0,"unit":"count"}',
  '{"operator":"<=","threshold":0}',NULL,1,'SYSTEM_DEFAULT','system',
  'msdb suspect_pages 存在未修复的 823、824、校验和或页撕裂错误线索；需核对错误日志、存储和人工一致性检查结果',TRUE)
ON CONFLICT(rule_code) DO UPDATE SET
 rule_name=EXCLUDED.rule_name,rule_level=EXCLUDED.rule_level,metric_name=EXCLUDED.metric_name,
 condition_config=EXCLUDED.condition_config,recovery_config=EXCLUDED.recovery_config,
 description=EXCLUDED.description,recommended=EXCLUDED.recommended;