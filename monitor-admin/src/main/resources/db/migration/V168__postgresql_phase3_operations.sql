-- PostgreSQL 第三期：日志/审计、复制/HA、备份恢复与运维任务中心。

ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS pg_log_path VARCHAR(1000);
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS pg_log_format VARCHAR(16) NOT NULL DEFAULT 'json';
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS pg_patroni_url VARCHAR(1000);
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS pg_backup_type VARCHAR(32);
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS pg_backup_path VARCHAR(1000);
COMMENT ON COLUMN db_instance.pg_log_path IS 'collector 节点可读的 PG 日志文件路径；不存储凭据';
COMMENT ON COLUMN db_instance.pg_patroni_url IS 'Patroni REST API 只读地址；不允许 URL 内嵌凭据';
COMMENT ON COLUMN db_instance.pg_backup_path IS 'pgBackRest/Barman JSON 状态文件路径；不存储仓库凭据';

CREATE TABLE IF NOT EXISTS pg_operational_event (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    category VARCHAR(32) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'info',
    database_name VARCHAR(128),
    user_name VARCHAR(128),
    object_name VARCHAR(256),
    query_id VARCHAR(64),
    sql_state VARCHAR(10),
    message TEXT,
    fingerprint VARCHAR(64),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    sensitive_redacted BOOLEAN NOT NULL DEFAULT TRUE,
    event_time TIMESTAMPTZ NOT NULL,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pg_operational_event_instance_time
    ON pg_operational_event(instance_id, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_pg_operational_event_search
    ON pg_operational_event(instance_id, category, event_type, sql_state, event_time DESC);
CREATE INDEX IF NOT EXISTS idx_pg_operational_event_fingerprint
    ON pg_operational_event(instance_id, fingerprint, event_time DESC);

CREATE TABLE IF NOT EXISTS pg_restore_drill (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    backup_id VARCHAR(128),
    target_time TIMESTAMPTZ,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    status VARCHAR(24) NOT NULL,
    validation_result VARCHAR(32) NOT NULL DEFAULT 'unverified',
    duration_seconds BIGINT,
    owner_name VARCHAR(128) NOT NULL,
    notes TEXT,
    created_by BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_pg_restore_drill_instance
    ON pg_restore_drill(instance_id, started_at DESC);

INSERT INTO sys_menu (name,code,menu_type,type,icon,route,component,perm,sort,status,visible,parent_id,description)
VALUES
('日志与审计','pg_log_audit','menu','实例级','Document','log-audit','monitor/pg/log-audit','pg_log:view',9,'enabled',TRUE,(SELECT id FROM sys_menu WHERE code='monitor_pg'),'PostgreSQL 日志检索、错误指纹、pgaudit 审计与敏感信息脱敏'),
('HA 集群','pg_ha','menu','实例级','Share','ha','monitor/pg/ha','pg_ha:view',10,'enabled',TRUE,(SELECT id FROM sys_menu WHERE code='monitor_pg'),'Patroni 成员、Leader、Timeline 与一致性快照，只读监控'),
('备份恢复','pg_backup','menu','实例级','Box','backup','monitor/pg/backup','pg_backup:view',11,'enabled',TRUE,(SELECT id FROM sys_menu WHERE code='monitor_pg'),'pgBackRest/Barman 备份状态、归档连续性与恢复演练'),
('任务进度','pg_progress','menu','实例级','Loading','progress','monitor/pg/progress','pg_progress:view',12,'enabled',TRUE,(SELECT id FROM sys_menu WHERE code='monitor_pg'),'VACUUM、CREATE INDEX、REINDEX、CLUSTER、COPY 与 BASE_BACKUP 进度')
ON CONFLICT(code) DO NOTHING;

UPDATE sys_menu SET buttons=COALESCE(buttons,'[]'::jsonb)||'[{"name":"查看逻辑复制","code":"pg_replication:logical","status":"enabled"}]'::jsonb
 WHERE code='pg_replication' AND NOT buttons @> '[{"code":"pg_replication:logical"}]'::jsonb;
UPDATE sys_menu SET buttons=COALESCE(buttons,'[]'::jsonb)||'[{"name":"查看审计","code":"pg_audit:view","status":"enabled"}]'::jsonb
 WHERE code='pg_log_audit' AND NOT buttons @> '[{"code":"pg_audit:view"}]'::jsonb;
UPDATE sys_menu SET buttons=COALESCE(buttons,'[]'::jsonb)||'[{"name":"管理恢复演练","code":"pg_restore_drill:manage","status":"enabled"}]'::jsonb
 WHERE code='pg_backup' AND NOT buttons @> '[{"code":"pg_restore_drill:manage"}]'::jsonb;

UPDATE sys_role SET permissions=permissions||'["pg_log:view","pg_replication:logical","pg_ha:view","pg_backup:view","pg_progress:view"]'::jsonb
 WHERE code IN ('dba','ops') AND NOT permissions @> '["pg_log:view","pg_replication:logical","pg_ha:view","pg_backup:view","pg_progress:view"]'::jsonb;
UPDATE sys_role SET permissions=permissions||'["pg_audit:view"]'::jsonb
 WHERE code IN ('dba','auditor') AND NOT permissions @> '["pg_audit:view"]'::jsonb;
UPDATE sys_role SET permissions=permissions||'["pg_restore_drill:manage"]'::jsonb
 WHERE code='dba' AND NOT permissions @> '["pg_restore_drill:manage"]'::jsonb;