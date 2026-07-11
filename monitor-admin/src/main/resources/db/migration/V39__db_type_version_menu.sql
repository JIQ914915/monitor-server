-- =============================================================
-- 数据库类型维护 + 数据库版本维护：归属「系统设置」分组
--   数据库类型：/system/db-type   → system/database-type
--   数据库版本：/system/db-version → system/database-version
--   权限码：db_type:* / db_version:*
-- =============================================================

INSERT INTO sys_menu (
    name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description
)
VALUES
(
    '数据库类型', 'system_db_type', 'menu', '系统级',
    'DataLine', 'db-type', 'system/database-type',
    'db_type:list', 14, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'system'),
    '数据库类型维护：JDBC 驱动/URL 模板/采集器实现类'
),
(
    '数据库版本', 'system_db_version', 'menu', '系统级',
    'Cpu', 'db-version', 'system/database-version',
    'db_version:list', 15, 'enabled', TRUE,
    (SELECT id FROM sys_menu WHERE code = 'system'),
    '数据库版本配置维护：推荐版本/废弃版本/EOL 日期'
)
ON CONFLICT (code) DO NOTHING;

-- 为预设的超级管理员角色（code=superadmin）追加权限码
UPDATE sys_role
   SET permissions = permissions
       || '["db_type:list","db_type:create","db_type:update","db_type:delete",
            "db_version:list","db_version:create","db_version:update","db_version:delete"]'::jsonb
 WHERE code = 'superadmin'
   AND NOT (permissions @> '["db_type:list"]'::jsonb);

-- 为预设的管理员角色（code=admin）追加只读权限码
UPDATE sys_role
   SET permissions = permissions
       || '["db_type:list","db_version:list"]'::jsonb
 WHERE code = 'admin'
   AND NOT (permissions @> '["db_type:list"]'::jsonb);

-- 菜单按钮权限点（挂在各菜单页面行）
UPDATE sys_menu
   SET buttons = '[
     {"name":"新增","code":"db_type:create","status":"enabled"},
     {"name":"编辑","code":"db_type:update","status":"enabled"},
     {"name":"删除","code":"db_type:delete","status":"enabled"}
   ]'::jsonb
 WHERE code = 'system_db_type';

UPDATE sys_menu
   SET buttons = '[
     {"name":"新增","code":"db_version:create","status":"enabled"},
     {"name":"编辑","code":"db_version:update","status":"enabled"},
     {"name":"删除","code":"db_version:delete","status":"enabled"}
   ]'::jsonb
 WHERE code = 'system_db_version';
