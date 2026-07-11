-- =============================================================
-- V70：告警事件状态 -> 分组映射（动态展示用）
--
-- 背景：
--   V69 已创建 alert_event_status / alert_event_status_group 字典。
--   本脚本补充分组映射，写入 sys_dict_item.remark（JSON）：
--     {"group":"firing|recovered|silenced|closed"}
--   前端据此动态归并“告警中/已恢复/已静默/已关闭”。
-- =============================================================

UPDATE sys_dict_item
SET remark = '{"group":"firing"}'
WHERE dict_type = 'alert_event_status'
  AND item_value IN ('pending', 'confirmed', 'handling');

UPDATE sys_dict_item
SET remark = '{"group":"recovered"}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'recovered';

UPDATE sys_dict_item
SET remark = '{"group":"silenced"}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'ignored';

UPDATE sys_dict_item
SET remark = '{"group":"closed"}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'closed';

