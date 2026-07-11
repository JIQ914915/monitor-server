-- =============================================================
-- 角色权限配置完善（参照原型「权限配置」）：
--   menu_perms        菜单与按钮权限矩阵（可见性/访问级别R|RW/按钮权限点）
--   data_scope        数据范围：all 全部 / group 指定分组 / self 仅本人负责
--   data_scope_groups 指定分组时的分组ID集合
-- 说明：permissions 仍为鉴权用的扁平权限码集合，由前端依据矩阵派生后写入。
-- =============================================================
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS menu_perms        JSONB       NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope        VARCHAR(16) NOT NULL DEFAULT 'all';
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS data_scope_groups JSONB       NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN sys_role.menu_perms IS '菜单与按钮权限矩阵：[{code,visible,access,buttons[]}]';
COMMENT ON COLUMN sys_role.data_scope IS '数据范围：all/group/self';
COMMENT ON COLUMN sys_role.data_scope_groups IS '数据范围为 group 时的分组ID集合';
