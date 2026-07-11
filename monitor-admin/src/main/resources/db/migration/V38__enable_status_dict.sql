-- =============================================================
-- 启用/停用状态字典化（enable_status）
--   菜单/角色/字典/字典项等模块的 status(enabled/disabled) 统一由字典驱动展示，
--   便于前端标签文案与颜色集中维护（承接 role_type / instance_status 字典模式）。
--   注：各表 status 列本就以 enabled/disabled 码存储，无需数据迁移，仅补充字典元数据。
-- =============================================================

INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('enable_status', '启用状态', '通用启用/停用状态（菜单、角色、字典等）')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort) VALUES
  ('enable_status', 'enabled',  '启用', 'success', 1),
  ('enable_status', 'disabled', '停用', 'info',    2)
ON CONFLICT DO NOTHING;
