-- =============================================================
-- 菜单管理完善：为菜单配置页面内的按钮权限点（参照原型「按钮权限配置」）
--   buttons: [{ name, code, status }]，code 建议 菜单编码:操作（如 user:create）
-- =============================================================
ALTER TABLE sys_menu ADD COLUMN IF NOT EXISTS buttons JSONB NOT NULL DEFAULT '[]'::jsonb;
COMMENT ON COLUMN sys_menu.buttons IS '按钮权限点：[{name,code,status}]';
