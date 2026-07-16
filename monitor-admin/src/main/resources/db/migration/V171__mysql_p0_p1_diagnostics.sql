-- MySQL P0/P1：8.4 显式支持、诊断状态字典、计划历史与新增容量指标。

ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS mysql_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS mysql_capabilities_detected_at TIMESTAMPTZ;

UPDATE database_version
   SET version_name = 'MySQL 8.4 LTS',
       supported_features = '{"adapter":"MySql84Adapter","performanceSchema":true,"sysSchema":true,"explainJson":true,"explainTree":true}'::jsonb,
       description = 'MySQL 8.4 LTS 显式适配；核心锁、复制、Performance Schema、告警与报告已纳入版本契约'
 WHERE lower(db_type) = 'mysql' AND version_code = '8.4';

INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('mysql_diagnostic_status', 'MySQL诊断状态', '正常/关注/告警/数据不足/暂不可用'),
  ('mysql_risk_level', 'MySQL风险等级', '容量、配置、计划与安全风险等级'),
  ('mysql_prediction_status', 'MySQL预测状态', '容量预测结论状态'),
  ('mysql_replication_stage', 'MySQL复制诊断阶段', '接收/排队/回放/外部资源'),
  ('mysql_replication_worker_status', 'MySQL复制Worker状态', '复制Worker运行状态'),
  ('mysql_replication_channel_status', 'MySQL复制通道状态', '复制通道运行状态'),
  ('mysql_config_baseline_template', 'MySQL配置基线模板', '稳定性、可观测与复制安全基线'),
  ('mysql_plan_change_status', 'MySQL计划变化状态', '执行计划是否变化'),
  ('mysql_advice_type', 'MySQL建议类型', '确定问题/风险提示/信息建议')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type,item_value,item_label,tag_type,sort)
SELECT v.dict_type,v.item_value,v.item_label,v.tag_type,v.sort FROM (VALUES
  ('mysql_diagnostic_status','normal','正常','success',1),
  ('mysql_diagnostic_status','attention','关注','warning',2),
  ('mysql_diagnostic_status','alert','告警','danger',3),
  ('mysql_diagnostic_status','no_data','数据不足','info',4),
  ('mysql_diagnostic_status','unavailable','暂不可用','info',5),
  ('mysql_risk_level','low','低风险','success',1),
  ('mysql_risk_level','medium','中风险','warning',2),
  ('mysql_risk_level','high','高风险','danger',3),
  ('mysql_risk_level','unknown','待确认','info',4),
  ('mysql_prediction_status','stable','增长稳定','success',1),
  ('mysql_prediction_status','risk','存在风险','danger',2),
  ('mysql_prediction_status','insufficient','历史数据不足','info',3),
  ('mysql_replication_stage','receive','接收异常','danger',1),
  ('mysql_replication_stage','queue','中继积压','warning',2),
  ('mysql_replication_stage','apply','回放异常','danger',3),
  ('mysql_replication_stage','resource','资源瓶颈','warning',4),
  ('mysql_replication_stage','normal','复制正常','success',5),
  ('mysql_replication_stage','unknown','数据不足','info',6),
  ('mysql_replication_worker_status','ON','运行中','success',1),
  ('mysql_replication_worker_status','OFF','已停止','danger',2),
  ('mysql_replication_channel_status','ON','运行中','success',1),
  ('mysql_replication_channel_status','OFF','异常','danger',2),
  ('mysql_replication_channel_status','UNKNOWN','未检测到','info',3),
  ('mysql_config_baseline_template','stability','稳定性基线','success',1),
  ('mysql_config_baseline_template','observability','可观测基线','info',2),
  ('mysql_config_baseline_template','replication_safety','复制安全基线','warning',3),
  ('mysql_plan_change_status','changed','计划已变化','warning',1),
  ('mysql_plan_change_status','unchanged','计划未变化','success',2),
  ('mysql_advice_type','problem','确定问题','danger',1),
  ('mysql_advice_type','risk','风险提示','warning',2),
  ('mysql_advice_type','info','信息建议','info',3)
) AS v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item d WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value);

CREATE TABLE IF NOT EXISTS mysql_plan_history (
  id BIGSERIAL PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  schema_name VARCHAR(128) NOT NULL,
  sql_hash VARCHAR(64) NOT NULL,
  plan_hash VARCHAR(64) NOT NULL,
  query_text TEXT NOT NULL,
  plan_format VARCHAR(16) NOT NULL,
  plan_json JSONB NOT NULL,
  node_summary JSONB NOT NULL DEFAULT '[]'::jsonb,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'low',
  conclusion TEXT,
  previous_plan_hash VARCHAR(64),
  plan_changed BOOLEAN NOT NULL DEFAULT FALSE,
  captured_by VARCHAR(128),
  captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(instance_id,schema_name,sql_hash,plan_hash)
);
CREATE INDEX IF NOT EXISTS idx_mysql_plan_history_query
  ON mysql_plan_history(instance_id,schema_name,sql_hash,captured_at DESC);

CREATE TABLE IF NOT EXISTS mysql_security_snapshot (
  id BIGSERIAL PRIMARY KEY,
  instance_id BIGINT NOT NULL,
  snapshot_hash VARCHAR(64) NOT NULL,
  snapshot_json JSONB NOT NULL,
  change_summary VARCHAR(500),
  captured_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_mysql_security_snapshot_instance_time
  ON mysql_security_snapshot(instance_id,captured_at DESC);

INSERT INTO metric_definition
(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES
('mysql.capacity.auto_increment_max_usage_pct','自增ID最高使用率','mysql','capacity','analysis','numeric','percent','capacity_risk','gauge','1d','业务表 AUTO_INCREMENT 当前值相对字段上限的最高使用率'),
('mysql.capacity.auto_increment_usage_pct','表自增ID使用率','mysql','capacity','analysis','numeric','percent','capacity_risk','gauge','1d','对象级 AUTO_INCREMENT 使用率，object_name 为 schema.table'),
('mysql.capacity.binlog_file_count','Binlog文件数','mysql','capacity','analysis','numeric','count','binlog_status','gauge','1h','SHOW BINARY LOGS 当前文件数'),
('mysql.var.binlog_expire_logs_seconds','Binlog保留秒数','mysql','config','raw','numeric','seconds','variables','gauge','1d','MySQL 8.0+ Binlog 自动过期秒数'),
('mysql.var.expire_logs_days','Binlog保留天数','mysql','config','raw','numeric','days','variables','gauge','1d','MySQL 5.6/5.7 Binlog 自动过期天数'),
('mysql.var.performance_schema_digests_size','语句摘要槽位数','mysql','config','raw','numeric','count','variables','gauge','1d','Performance Schema 语句摘要容量，0 表示 Top SQL 能力不可用'),
('mysql.var.performance_schema_max_digest_length','摘要最大长度','mysql','config','raw','numeric','bytes','variables','gauge','1d','Performance Schema SQL 摘要最大长度'),
('mysql.var.performance_schema_max_sql_text_length','SQL文本最大长度','mysql','config','raw','numeric','bytes','variables','gauge','1d','Performance Schema SQL 原文最大长度'),
('mysql.var_text.performance_schema','Performance Schema开关','mysql','config','raw','text',NULL,'variables','state','1d','Performance Schema 全局开关'),
('mysql.var_text.query_cache_type','查询缓存模式','mysql','config','raw','text',NULL,'variables','state','1d','MySQL 5.6/5.7 查询缓存模式，8.0 已移除')
ON CONFLICT (metric_code) DO NOTHING;

INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort)
SELECT 'report_type','capacity','容量专项','warning',6
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_type='report_type' AND item_value='capacity');

INSERT INTO mysql_param_meta(param_name,display_name,category,is_dynamic,unit,description,min_version,max_version)
VALUES
('binlog_expire_logs_seconds','Binlog保留时长','logging',TRUE,'seconds','MySQL 8.0+ Binlog 自动过期秒数，用于空间风险评估。','8.0',NULL),
('expire_logs_days','Binlog保留天数','logging',TRUE,'days','MySQL 5.6/5.7 Binlog 自动过期天数。','5.6','5.7')
ON CONFLICT(param_name) DO NOTHING;