-- PostgreSQL 第一期增强：14-18、对象采集范围、实时会话权限、PG18 I/O 字节指标。

ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS pg_object_scope VARCHAR(16) NOT NULL DEFAULT 'monitoring';
ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS pg_object_databases JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS pg_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS pg_capabilities_detected_at TIMESTAMPTZ;
COMMENT ON COLUMN db_instance.pg_object_scope IS 'PG对象级采集范围：monitoring/selected/all';
COMMENT ON COLUMN db_instance.pg_object_databases IS 'PG对象级采集选定数据库列表，pg_object_scope=selected 时生效';

INSERT INTO database_version (db_type, version_code, version_name, sort_order, description) VALUES
('postgresql', '17', 'PostgreSQL 17', 5, '社区支持至 2029-11'),
('postgresql', '18', 'PostgreSQL 18', 6, '社区支持至 2030-11')
ON CONFLICT (db_type, version_code) DO UPDATE
SET version_name = EXCLUDED.version_name,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description;

UPDATE database_version
   SET description = '已于 2025-11 结束社区支持，仅保留兼容基线'
 WHERE lower(db_type) = 'postgresql' AND version_code = '13';

INSERT INTO metric_definition
(metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description)
VALUES
('pg.io.read_bytes_rate',   'I/O读取字节速率', 'postgresql', 'performance', 'analysis', 'numeric', 'bytes', 'pg.pg_stat_io', 'delta', '1m', '每秒读取字节数（PostgreSQL 18+）'),
('pg.io.write_bytes_rate',  'I/O写入字节速率', 'postgresql', 'performance', 'analysis', 'numeric', 'bytes', 'pg.pg_stat_io', 'delta', '1m', '每秒写入字节数（PostgreSQL 18+）'),
('pg.io.extend_bytes_rate', '文件扩展字节速率', 'postgresql', 'performance', 'analysis', 'numeric', 'bytes', 'pg.pg_stat_io', 'delta', '1m', '每秒文件扩展字节数（PostgreSQL 18+）')
ON CONFLICT (metric_code) DO NOTHING;

UPDATE sys_menu
   SET buttons = buttons || '[{"name":"查看实时会话","code":"pg_session:view","status":"enabled"}]'::jsonb
 WHERE code = 'pg_realtime'
   AND NOT (buttons @> '[{"code":"pg_session:view"}]'::jsonb);
UPDATE sys_menu
   SET buttons = buttons || '[{"name":"取消查询","code":"pg_session:cancel","status":"enabled"}]'::jsonb
 WHERE code = 'pg_realtime'
   AND NOT (buttons @> '[{"code":"pg_session:cancel"}]'::jsonb);
UPDATE sys_menu
   SET buttons = buttons || '[{"name":"终止会话","code":"pg_session:terminate","status":"enabled"}]'::jsonb
 WHERE code = 'pg_realtime'
   AND NOT (buttons @> '[{"code":"pg_session:terminate"}]'::jsonb);

UPDATE sys_role SET permissions = permissions || '["pg_session:view"]'::jsonb
 WHERE code IN ('dba','ops','auditor') AND NOT (permissions @> '["pg_session:view"]'::jsonb);
UPDATE sys_role SET permissions = permissions || '["pg_session:cancel"]'::jsonb
 WHERE code IN ('dba','ops') AND NOT (permissions @> '["pg_session:cancel"]'::jsonb);
UPDATE sys_role SET permissions = permissions || '["pg_session:terminate"]'::jsonb
 WHERE code = 'dba' AND NOT (permissions @> '["pg_session:terminate"]'::jsonb);

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT 'capability_status', 'permission_denied', '权限不足', 'danger', 7
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item
   WHERE dict_type = 'capability_status' AND item_value = 'permission_denied');
