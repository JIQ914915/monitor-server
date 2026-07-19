-- PostgreSQL P0：采集正确性、质量状态、统计重置与有界运维快照。
-- V185 为创建本迁移时的最新版本；禁止回改已执行的 V168 等历史迁移。

CREATE TABLE pg_collect_item_status (
    instance_id BIGINT NOT NULL,
    frequency VARCHAR(5) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    duration_ms INT NOT NULL DEFAULT 0,
    row_count INT NOT NULL DEFAULT 0,
    consecutive_failures INT NOT NULL DEFAULT 0,
    last_success_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instance_id, frequency, item_code)
);
COMMENT ON TABLE pg_collect_item_status IS 'PostgreSQL 采集项当前质量状态，按实例、频率、采集项覆盖更新';
CREATE INDEX idx_pg_collect_item_status_problem
    ON pg_collect_item_status(status, collected_at DESC) WHERE status != 'success';

CREATE TABLE pg_operational_snapshot (
    instance_id BIGINT NOT NULL,
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    database_name VARCHAR(128),
    user_name VARCHAR(128),
    object_name VARCHAR(256),
    query_id VARCHAR(64),
    sql_state VARCHAR(10),
    message TEXT,
    fingerprint VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    sensitive_redacted BOOLEAN NOT NULL DEFAULT TRUE,
    event_time TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (instance_id, fingerprint)
);
COMMENT ON TABLE pg_operational_snapshot IS 'PostgreSQL 原生复制、归档和任务进度当前快照；相同对象覆盖更新';
CREATE INDEX idx_pg_operational_snapshot_search
    ON pg_operational_snapshot(instance_id, category, event_type, severity, event_time DESC);

-- 用既有事件的最近一条初始化当前快照，升级后页面无需等待下一轮采集。
INSERT INTO pg_operational_snapshot
(instance_id,source,category,event_type,severity,database_name,user_name,object_name,
 query_id,sql_state,message,fingerprint,payload,sensitive_redacted,event_time,collected_at)
SELECT DISTINCT ON (instance_id,fingerprint)
 instance_id,source,category,event_type,severity,database_name,user_name,object_name,
 query_id,sql_state,message,fingerprint,payload,sensitive_redacted,event_time,collected_at
FROM pg_operational_event
WHERE fingerprint IS NOT NULL AND category != 'audit'
ORDER BY instance_id,fingerprint,event_time DESC;

INSERT INTO sys_dict_type(dict_type,dict_name,remark) VALUES
('pg_collect_item_status','PG 采集项状态','PostgreSQL 单项采集当前执行状态'),
('pg_collect_failure_reason','PG 采集不可用原因','PostgreSQL 采集失败或能力不可用原因')
ON CONFLICT(dict_type) DO NOTHING;

INSERT INTO sys_dict_item(dict_type,item_value,item_label,tag_type,sort,status)
SELECT v.dict_type,v.item_value,v.item_label,v.tag_type,v.sort,'enabled'
FROM (VALUES
 ('pg_collect_item_status','success','正常','success',1),
 ('pg_collect_item_status','unavailable','不可用','info',2),
 ('pg_collect_item_status','partial_failed','部分失败','warning',3),
 ('pg_collect_item_status','failed','失败','danger',4),
 ('pg_collect_failure_reason','none','无','success',1),
 ('pg_collect_failure_reason','not_enabled','目标库未启用','info',2),
 ('pg_collect_failure_reason','unsupported','当前版本不支持','info',3),
 ('pg_collect_failure_reason','permission_denied','采集账号缺少权限','warning',4),
 ('pg_collect_failure_reason','timeout','目标库查询超时','warning',5),
 ('pg_collect_failure_reason','connection_failed','目标库连接中断','danger',6),
 ('pg_collect_failure_reason','collection_failed','采集执行失败','danger',7)
) AS v(dict_type,item_value,item_label,tag_type,sort)
WHERE NOT EXISTS (
 SELECT 1 FROM sys_dict_item d WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value
);

INSERT INTO metric_definition
(metric_code,metric_name,db_type,domain,layer,value_type,unit,source_collector,process_type,frequency,description)
VALUES
('pg.stats.database_reset_age_seconds','数据库统计持续时间','postgresql','collection','explain','numeric','seconds','pg.database_stat','raw','1m','pg_stat_database 距最近统计重置的秒数，用于识别差值基线失效'),
('pg.stats.wal_reset_age_seconds','WAL 统计持续时间','postgresql','collection','explain','numeric','seconds','pg_wal','raw','1m','pg_stat_wal 距最近统计重置的秒数'),
('pg.stats.io_reset_age_seconds','I/O 统计持续时间','postgresql','collection','explain','numeric','seconds','pg_stat_io','raw','1m','PG16+ pg_stat_io 距最近统计重置的秒数'),
('pg.statements.reset_age_seconds','SQL 统计持续时间','postgresql','sql','explain','numeric','seconds','pg_top_sql','raw','1h','pg_stat_statements 距最近统计重置的秒数'),
('pg.statements.dealloc_delta','SQL 统计淘汰增量','postgresql','sql','guard','numeric','count','pg_top_sql','delta','1h','本周期 pg_stat_statements 条目因容量不足被淘汰的次数')
ON CONFLICT(metric_code) DO UPDATE SET
 metric_name=EXCLUDED.metric_name,domain=EXCLUDED.domain,layer=EXCLUDED.layer,
 value_type=EXCLUDED.value_type,unit=EXCLUDED.unit,source_collector=EXCLUDED.source_collector,
 process_type=EXCLUDED.process_type,frequency=EXCLUDED.frequency,description=EXCLUDED.description;
