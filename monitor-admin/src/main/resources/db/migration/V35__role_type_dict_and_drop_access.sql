-- =============================================================
-- 角色管理整改：① 角色类型改为字典值（role_type）；② 移除菜单权限矩阵中的「访问级别」access
--   背景：
--     - 角色类型此前以中文自由串 '预设'/'自定义' 存储，改为字典 role_type（preset/custom），前端下拉走字典。
--     - menu_perms(jsonb) 中的 access(R/RW) 在鉴权中未被使用（读写语义已由按钮权限 view/create/update/delete 表达），移除冗余字段。
-- =============================================================

-- ---- 1) 字典：角色类型 role_type ----
INSERT INTO sys_dict_type (dict_type, dict_name, remark)
VALUES ('role_type', '角色类型', '系统角色类型（预设不可删除）')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort) VALUES
  ('role_type', 'preset', '预设',   'success', 1),
  ('role_type', 'custom', '自定义', 'info',    2);

-- ---- 2) 迁移 sys_role.type：中文串 → 字典值 ----
UPDATE sys_role SET type = 'preset' WHERE type = '预设';
UPDATE sys_role SET type = 'custom' WHERE type = '自定义' OR type IS NULL OR type = '';
ALTER TABLE sys_role ALTER COLUMN type SET DEFAULT 'custom';
COMMENT ON COLUMN sys_role.type IS '角色类型：字典 role_type 值（preset 预设 / custom 自定义；预设不可删除）';

-- ---- 3) 剥离 menu_perms(jsonb) 中的 access 字段 ----
UPDATE sys_role
   SET menu_perms = (
       SELECT COALESCE(jsonb_agg(elem - 'access'), '[]'::jsonb)
         FROM jsonb_array_elements(menu_perms) elem
   )
 WHERE menu_perms IS NOT NULL
   AND jsonb_typeof(menu_perms) = 'array'
   AND jsonb_array_length(menu_perms) > 0;
COMMENT ON COLUMN sys_role.menu_perms IS '菜单与按钮权限矩阵：[{code,visible,buttons[]}]（已移除 access 访问级别）';
