-- =============================================================
-- V69：告警事件状态字典与分组字典
--
-- 目的：
--   1) 统一事件状态码与前端展示文案/颜色；
--   2) 统一“主视图四态分组”（告警中/已恢复/已静默/已关闭）筛选口径。
-- =============================================================

-- ---- 1. 事件状态字典（后端真实状态）----
INSERT INTO sys_dict_type (dict_type, dict_name, remark)
VALUES ('alert_event_status', '告警事件状态', '告警事件真实生命周期状态：pending/confirmed/handling/recovered/ignored/closed')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('alert_event_status', 'pending',   '待处理', 'danger',  1),
  ('alert_event_status', 'confirmed', '已确认', 'warning', 2),
  ('alert_event_status', 'handling',  '处理中', 'primary', 3),
  ('alert_event_status', 'recovered', '已恢复', 'success', 4),
  ('alert_event_status', 'ignored',   '已静默', 'info',    5),
  ('alert_event_status', 'closed',    '已关闭', 'warning', 6)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item i
  WHERE i.dict_type = v.dict_type AND i.item_value = v.item_value
);

-- ---- 2. 状态分组字典（前端主视图四态）----
INSERT INTO sys_dict_type (dict_type, dict_name, remark)
VALUES ('alert_event_status_group', '告警事件状态分组', '告警事件中心主视图四态分组：firing/recovered/silenced/closed')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('alert_event_status_group', 'firing',    '告警中', 'danger',  1),
  ('alert_event_status_group', 'recovered', '已恢复', 'success', 2),
  ('alert_event_status_group', 'silenced',  '已静默', 'info',    3),
  ('alert_event_status_group', 'closed',    '已关闭', 'warning', 4),
  ('alert_event_status_group', 'all',       '全部状态', '',       99)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item i
  WHERE i.dict_type = v.dict_type AND i.item_value = v.item_value
);

