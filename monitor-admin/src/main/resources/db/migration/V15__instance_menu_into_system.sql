-- =============================================================
-- 实例管理并入「系统设置」分组：
--   实例管理/实例详情 挂到 system 分组下 → 路由 /system/instances(/detail/:id)
--   前端组件迁移至 views/system/instance/*，同步更新 component
--   菜单访问权限码 instance:list → system_instance（与 system_* 同级命名一致）
-- =============================================================

UPDATE sys_menu
   SET parent_id  = (SELECT id FROM sys_menu WHERE code = 'system'),
       sort       = 9,
       component  = 'system/instance/list',
       perm       = 'system_instance'
 WHERE code = 'instances';

UPDATE sys_menu
   SET parent_id   = (SELECT id FROM sys_menu WHERE code = 'system'),
       component   = 'system/instance/detail',
       active_menu = '/system/instances'
 WHERE code = 'instance_detail';

-- 预设角色权限码同步：instance:list → system_instance
UPDATE sys_role
   SET permissions = REPLACE(permissions::text, '"instance:list"', '"system_instance"')::jsonb
 WHERE permissions::text LIKE '%"instance:list"%';
