-- SQL Server 批次 C：阻塞现场、死锁事件、Top SQL 与 Query Store 降级状态。
ALTER TABLE metric_top_sql ADD COLUMN IF NOT EXISTS delta_physical_reads BIGINT;
ALTER TABLE metric_top_sql ADD COLUMN IF NOT EXISTS delta_writes BIGINT;
COMMENT ON COLUMN metric_top_sql.delta_physical_reads IS '周期内物理读取数，SQL Server Top SQL 使用';
COMMENT ON COLUMN metric_top_sql.delta_writes IS '周期内逻辑写入数，SQL Server Top SQL 使用';
CREATE TABLE IF NOT EXISTS sqlserver_diagnostic_event (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    database_name VARCHAR(128),
    severity VARCHAR(16) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    sensitive_redacted BOOLEAN NOT NULL DEFAULT TRUE,
    event_time TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_sqlserver_diagnostic_event UNIQUE(instance_id,event_type,fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_sqlserver_diagnostic_event_time
    ON sqlserver_diagnostic_event(instance_id,event_time DESC);

INSERT INTO sys_dict_type(dict_type,dict_name,remark) VALUES
 ('sqlserver_query_store_collect_state','SQL Server Query Store 采集状态','Top SQL 数据源状态')
ON CONFLICT(dict_type) DO NOTHING;

INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT * FROM (VALUES
 ('sqlserver_query_store_collect_state','available','Query Store 可用','success',1),
 ('sqlserver_query_store_collect_state','dmv_fallback','Query Store 不可用，已降级为实时 DMV','warning',2),
 ('sqlserver_query_store_collect_state','permission_denied','采集账号权限不足','danger',3)
) v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_item d
 WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

INSERT INTO metric_definition
(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES ('sqlserver.query_store.collect_state','Query Store 采集状态','sqlserver','sql','explain','text',NULL,
        'top_sql','state','1h','available=历史数据可用；dmv_fallback=未启用、只读或无权限，已降级且不会自动开启')
ON CONFLICT(metric_code) DO NOTHING;

INSERT INTO alert_rule
(rule_name,rule_code,rule_level,db_type_id,db_version_ids,metric_name,condition_config,recovery_config,
 notification_config,scan_interval_min,scan_interval_source,created_by,description,recommended)
VALUES ('SQL Server 发生死锁','builtin.sqlserver.deadlock','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.deadlocks_per_sec',
 '{"operator":">","threshold":0.0,"duration":0,"unit":"qps"}',
 '{"operator":"<=","threshold":0.0}',NULL,1,'SYSTEM_DEFAULT','system',
 '检测到死锁；系统保留脱敏死锁图供复盘加锁顺序，不提供自动终止会话动作',TRUE)
ON CONFLICT(rule_code) DO NOTHING;
