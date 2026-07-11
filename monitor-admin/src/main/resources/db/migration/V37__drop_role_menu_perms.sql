-- =============================================================
-- 移除 sys_role.menu_perms（冗余字段）
--   运行时鉴权与菜单渲染仅依赖扁平 permissions(jsonb) 权限码集合；
--   menu_perms 仅用于角色权限配置弹窗回显，且该弹窗已能从 permissions 反推勾选状态，
--   故删除该列以消除冗余（承接 1.1.26 移除 access 后的进一步收敛）。
-- =============================================================
ALTER TABLE sys_role DROP COLUMN IF EXISTS menu_perms;
