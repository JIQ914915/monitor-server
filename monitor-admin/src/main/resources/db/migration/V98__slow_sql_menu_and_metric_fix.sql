-- =============================================================
-- V98: 慢SQL分析模块
--   1) slowsql 菜单改名「慢SQL分析」，补权限码 slowsql:view / slowsql:export
--   2) 角色权限同步（dba/ops 查看+导出，auditor 仅查看）
--   3) 修复 V55 临时表增量指标定义大小写：采集侧（GlobalStatusItem）落库
--      metric_code 统一为小写，metric_definition 中的编码需与之一致
-- =============================================================

-- ── 菜单：改名 + 权限码 + 导出按钮 ─────────────────────────────
UPDATE sys_menu
   SET name        = '慢SQL分析',
       perm        = 'slowsql:view',
       description = '慢SQL分析：时间窗口内 Top SQL 指纹排名、执行统计与单指纹趋势下钻（数据源 performance_schema digest 小时级增量）',
       buttons     = '[
         {"name": "导出数据", "code": "slowsql:export", "status": "enabled"}
       ]'::jsonb
 WHERE code = 'slowsql';

-- ── 角色权限码同步 ─────────────────────────────────────────────
-- super_admin：已拥有通配 *:*，无需单独添加

-- dba / ops：查看 + 导出
UPDATE sys_role
   SET permissions = permissions
       || '["slowsql:view", "slowsql:export"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["slowsql:view"]'::jsonb);

-- auditor：仅查看
UPDATE sys_role
   SET permissions = permissions
       || '["slowsql:view"]'::jsonb
 WHERE code = 'auditor'
   AND NOT (permissions @> '["slowsql:view"]'::jsonb);

-- ── 指标定义大小写修复（与实际落库 metric_code 对齐） ─────────
UPDATE metric_definition
   SET metric_code = 'mysql.delta.created_tmp_tables'
 WHERE metric_code = 'mysql.delta.Created_tmp_tables'
   AND NOT EXISTS (SELECT 1 FROM metric_definition WHERE metric_code = 'mysql.delta.created_tmp_tables');

UPDATE metric_definition
   SET metric_code = 'mysql.delta.created_tmp_disk_tables'
 WHERE metric_code = 'mysql.delta.Created_tmp_disk_tables'
   AND NOT EXISTS (SELECT 1 FROM metric_definition WHERE metric_code = 'mysql.delta.created_tmp_disk_tables');
