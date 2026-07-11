-- =============================================================
-- V140: 实例能力状态字典（需求 §20 运行时能力检测与页面降级）
--   capability_status：实例各监控能力的运行时状态，
--   由 POST /instances/capabilities 返回，前端能力横幅按字典解析文案与颜色。
-- 幂等：类型 ON CONFLICT 跳过；字典项 NOT EXISTS 防重。
-- =============================================================

INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('capability_status', '能力状态', '实例监控能力运行时状态（可用/受限/版本不支持/不适用/采集异常/数据不足）')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('capability_status', 'available',           '可用',       'success', 1),
  ('capability_status', 'limited',             '部分可用',   'warning', 2),
  ('capability_status', 'version_not_support', '版本不支持', 'info',    3),
  ('capability_status', 'not_applicable',      '不适用',     'info',    4),
  ('capability_status', 'collect_error',       '采集异常',   'danger',  5),
  ('capability_status', 'no_data',             '数据不足',   'warning', 6)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
   WHERE d.dict_type = v.dict_type AND d.item_value = v.item_value);
