-- SQL Server 批次 D：备份覆盖、Always On、恢复演练登记与基础健康告警。
CREATE TABLE IF NOT EXISTS sqlserver_restore_drill (
 id BIGSERIAL PRIMARY KEY,instance_id BIGINT NOT NULL,backup_reference VARCHAR(256),
 started_at TIMESTAMPTZ NOT NULL,finished_at TIMESTAMPTZ,status VARCHAR(24) NOT NULL,
 validation_result VARCHAR(32) NOT NULL DEFAULT 'unverified',rto_seconds BIGINT,
 owner_name VARCHAR(128) NOT NULL,notes TEXT,created_by BIGINT,created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sqlserver_restore_drill_instance ON sqlserver_restore_drill(instance_id,started_at DESC);

INSERT INTO sys_dict_type(dict_type,dict_name,remark) VALUES
 ('sqlserver_ha_role','SQL Server HA 角色','可用组角色'),
 ('sqlserver_sync_state','SQL Server 同步状态','可用数据库同步状态'),
 ('sqlserver_backup_status','SQL Server 备份状态','备份覆盖和新鲜度状态'),
 ('sqlserver_restore_validation','SQL Server 恢复验证','人工恢复演练验证结果'),
 ('sqlserver_restore_drill_status','SQL Server 恢复演练状态','人工恢复演练流程状态')
ON CONFLICT(dict_type) DO NOTHING;
INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('sqlserver_ha_role','0','未知','info',9),('sqlserver_ha_role','1','主副本','success',1),('sqlserver_ha_role','2','辅助副本','primary',2),
 ('sqlserver_sync_state','HEALTHY','同步健康','success',1),('sqlserver_sync_state','PARTIALLY_HEALTHY','部分健康','warning',2),('sqlserver_sync_state','NOT_HEALTHY','不同步','danger',3),
 ('sqlserver_backup_status','normal','备份覆盖正常','success',1),('sqlserver_backup_status','overdue','备份已逾期','danger',2),('sqlserver_backup_status','uncovered','数据库未覆盖','danger',3),
 ('sqlserver_restore_validation','unverified','未验证','info',1),('sqlserver_restore_validation','passed','验证通过','success',2),('sqlserver_restore_validation','failed','验证失败','danger',3),
 ('sqlserver_restore_drill_status','planned','计划中','info',1),('sqlserver_restore_drill_status','running','进行中','warning',2),('sqlserver_restore_drill_status','completed','已完成','success',3),('sqlserver_restore_drill_status','failed','失败','danger',4)
)v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO metric_definition(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES
 ('sqlserver.backup.max_full_age_hours','用户库最久完整备份年龄','sqlserver','backup','guard','numeric','count','backup','raw','1h','用户数据库最近完整备份最大间隔小时'),
 ('sqlserver.backup.max_log_age_minutes','用户库最久日志备份年龄','sqlserver','backup','guard','numeric','count','backup','raw','1h','FULL/BULK_LOGGED 数据库最近日志备份最大间隔分钟'),
 ('sqlserver.backup.uncovered_database_count','未覆盖用户数据库','sqlserver','backup','guard','numeric','count','backup','raw','1h','没有完整备份记录的用户数据库数'),
 ('sqlserver.backup.log_missing_database_count','缺少日志备份数据库','sqlserver','backup','guard','numeric','count','backup','raw','1h','FULL/BULK_LOGGED 且无日志备份的用户数据库数'),
 ('sqlserver.backup.readiness_notice','备份恢复准备提示','sqlserver','backup','explain','text',NULL,'backup','state','1h','明确区分备份历史与恢复验证'),
 ('sqlserver.ag.disconnected_replicas','AG 断连副本数','sqlserver','ha','guard','numeric','count','always_on','raw','1m','本地可见的断连副本'),
 ('sqlserver.ag.unhealthy_databases','AG 非健康数据库数','sqlserver','ha','guard','numeric','count','always_on','raw','1m','同步健康非 HEALTHY 的数据库数'),
 ('sqlserver.ag.suspended_databases','AG 数据移动暂停数','sqlserver','ha','guard','numeric','count','always_on','raw','1m','is_suspended 数据库数'),
 ('sqlserver.ag.max_log_send_queue_kb','AG 最大发送队列','sqlserver','ha','analysis','numeric','kb','always_on','raw','1m','最大 log send queue'),
 ('sqlserver.ag.max_redo_queue_kb','AG 最大重做队列','sqlserver','ha','analysis','numeric','kb','always_on','raw','1m','最大 redo queue'),
 ('sqlserver.ag.max_send_seconds','AG 估算最大发送秒数','sqlserver','ha','guard','numeric','count','always_on','ratio','1m','队列除以发送速率，需结合持续时间解释'),
 ('sqlserver.ag.max_redo_seconds','AG 估算最大重做秒数','sqlserver','ha','guard','numeric','count','always_on','ratio','1m','队列除以重做速率，需结合持续时间解释')
ON CONFLICT(metric_code) DO NOTHING;

INSERT INTO alert_rule(rule_name,rule_code,rule_level,db_type_id,metric_name,condition_config,recovery_config,scan_interval_min,scan_interval_source,created_by,description,recommended)
VALUES
 ('SQL Server 完整备份逾期','builtin.sqlserver.backup.full_overdue','level_1',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.backup.max_full_age_hours','{"operator":">","threshold":24,"duration":0,"unit":"hour"}','{"operator":"<=","threshold":24}',5,'SYSTEM_DEFAULT','system','用户数据库完整备份超过 24 小时；备份记录不代表已验证可恢复',TRUE),
 ('SQL Server 数据库未纳入备份','builtin.sqlserver.backup.uncovered','level_1',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.backup.uncovered_database_count','{"operator":">","threshold":0,"duration":0,"unit":"count"}','{"operator":"<=","threshold":0}',5,'SYSTEM_DEFAULT','system','存在没有完整备份记录的用户数据库，请核对备份策略',TRUE),
 ('SQL Server AG 副本断连','builtin.sqlserver.ag.disconnected','level_1',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.ag.disconnected_replicas','{"operator":">","threshold":0,"duration":180,"unit":"count"}','{"operator":"<=","threshold":0}',1,'SYSTEM_DEFAULT','system','Always On 副本持续断连；只提供排查建议，不自动故障转移',TRUE),
 ('SQL Server AG 发送积压','builtin.sqlserver.ag.send_lag','level_2',(SELECT id FROM database_type WHERE code='SQLSERVER'),'sqlserver.ag.max_send_seconds','{"operator":">","threshold":300,"duration":300,"unit":"second"}','{"operator":"<=","threshold":120}',1,'SYSTEM_DEFAULT','system','发送队列按当前速率估算超过 5 分钟，请结合同步提交模式、网络和日志生成速率判断',TRUE)
ON CONFLICT(rule_code) DO NOTHING;

UPDATE sys_role SET permissions=permissions||'["sqlserver_backup:view","sqlserver_restore_drill:manage"]'::jsonb
 WHERE code IN ('dba','ops') AND NOT permissions @> '["sqlserver_backup:view","sqlserver_restore_drill:manage"]'::jsonb;
