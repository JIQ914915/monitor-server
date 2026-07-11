-- =============================================================
-- V97：告警事件状态 -> 可用操作映射（操作列按钮动态展示用）
--
-- 背景：
--   V70 已在 sys_dict_item.remark 写入分组映射 {"group":"..."}。
--   本脚本在同一 JSON 中补充 actions 字段：
--     {"group":"firing","actions":["confirm"]}
--   前端据此控制操作列按钮的显示与否，替代前端硬编码状态判断。
--
--   按严格串行处置流程展示（每个状态只显示下一步操作）：
--     pending   -> 确认（confirm）
--     confirmed -> 受理（handling）
--     handling  -> 静默（silence，可选抑制）/ 关闭（close，终结处置）
--     recovered / ignored / closed -> 无操作
-- =============================================================

UPDATE sys_dict_item
SET remark = '{"group":"firing","actions":["confirm"]}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'pending';

UPDATE sys_dict_item
SET remark = '{"group":"firing","actions":["handling"]}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'confirmed';

UPDATE sys_dict_item
SET remark = '{"group":"firing","actions":["silence","close"]}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'handling';

UPDATE sys_dict_item
SET remark = '{"group":"recovered","actions":[]}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'recovered';

UPDATE sys_dict_item
SET remark = '{"group":"silenced","actions":[]}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'ignored';

UPDATE sys_dict_item
SET remark = '{"group":"closed","actions":[]}'
WHERE dict_type = 'alert_event_status'
  AND item_value = 'closed';
