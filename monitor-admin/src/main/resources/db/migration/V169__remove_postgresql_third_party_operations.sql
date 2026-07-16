-- 移除 PostgreSQL 第三期的文件型/第三方数据源，保留系统视图直采、WAL 归档、任务进度和恢复演练。

ALTER TABLE db_instance DROP COLUMN IF EXISTS pg_log_path;
ALTER TABLE db_instance DROP COLUMN IF EXISTS pg_log_format;
ALTER TABLE db_instance DROP COLUMN IF EXISTS pg_patroni_url;
ALTER TABLE db_instance DROP COLUMN IF EXISTS pg_backup_type;
ALTER TABLE db_instance DROP COLUMN IF EXISTS pg_backup_path;

DELETE FROM sys_dict_item WHERE dict_type IN ('pg_log_format', 'pg_backup_type');
DELETE FROM sys_dict_type WHERE dict_type IN ('pg_log_format', 'pg_backup_type');
DELETE FROM metric_definition WHERE metric_code = 'pg.ext.pgaudit';

-- V168 已在部分环境执行，后续补充的页面字典统一由新迁移初始化。
INSERT INTO sys_dict_type (dict_type,dict_name,remark) VALUES
('pg_operation_severity','PG 运维事件状态','复制、归档和任务进度的统一风险状态'),
('pg_restore_drill_status','PG 恢复演练状态','恢复演练执行状态'),
('pg_restore_validation_result','PG 恢复验证结果','恢复演练验证结论'),
('pg_plan_change_status','PG 执行计划变化状态','执行计划历史对比结果'),
('pg_object_scope','PG 对象采集范围','实例级对象采集范围'),
('common_boolean','通用是否状态','统一的是/否状态展示')
ON CONFLICT(dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type,item_value,item_label,tag_type,sort,status)
SELECT v.* FROM (VALUES
('pg_operation_severity','info','正常','success',1,'enabled'),
('pg_operation_severity','warning','关注','warning',2,'enabled'),
('pg_operation_severity','critical','告警','danger',3,'enabled'),
('pg_restore_drill_status','planned','待执行','info',1,'enabled'),
('pg_restore_drill_status','running','执行中','warning',2,'enabled'),
('pg_restore_drill_status','success','已完成','success',3,'enabled'),
('pg_restore_drill_status','failed','失败','danger',4,'enabled'),
('pg_restore_drill_status','cancelled','已取消','info',5,'enabled'),
('pg_restore_validation_result','unverified','未验证','warning',1,'enabled'),
('pg_restore_validation_result','partial','部分通过','warning',2,'enabled'),
('pg_restore_validation_result','passed','验证通过','success',3,'enabled'),
('pg_restore_validation_result','failed','验证失败','danger',4,'enabled'),
('pg_plan_change_status','unchanged','未变化','success',1,'enabled'),
('pg_plan_change_status','changed','已变化','warning',2,'enabled'),
('pg_object_scope','monitoring','仅监控连接库','primary',1,'enabled'),
('pg_object_scope','selected','指定数据库','warning',2,'enabled'),
('pg_object_scope','all','全部可连接数据库','danger',3,'enabled'),
('common_boolean','true','是','success',1,'enabled'),
('common_boolean','false','否','info',2,'enabled')
) v(dict_type,item_value,item_label,tag_type,sort,status)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item d
     WHERE d.dict_type=v.dict_type AND d.item_value=v.item_value
);

DELETE FROM sys_menu WHERE code = 'pg_ha';
UPDATE sys_menu
   SET name = '运维事件',
       description = 'PostgreSQL 原生复制、WAL 归档和任务进度事件时间线',
       buttons = COALESCE((
           SELECT jsonb_agg(button)
             FROM jsonb_array_elements(COALESCE(buttons, '[]'::jsonb)) AS button
            WHERE button ->> 'code' <> 'pg_audit:view'
       ), '[]'::jsonb)
 WHERE code = 'pg_log_audit';
UPDATE sys_menu
   SET name = '归档与恢复',
       description = 'WAL 归档状态、连续性风险与恢复演练'
 WHERE code = 'pg_backup';

UPDATE sys_role
   SET permissions = COALESCE(permissions, '[]'::jsonb) - 'pg_ha:view' - 'pg_audit:view'
 WHERE permissions ?| ARRAY['pg_ha:view', 'pg_audit:view'];

DELETE FROM pg_operational_event
 WHERE category = 'audit'
    OR source IN ('postgresql_log', 'patroni', 'pgbackrest', 'barman');