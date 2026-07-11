-- =============================================================
-- 告警级别改为四级体系：一级/二级/三级/四级，存入字典
--   rule_level 存量值迁移：
--     fatal    → level_1（一级：最高级别）
--     critical → level_2（二级：严重）
--     warning  → level_3（三级：预警）
--     info     → level_4（四级：提示）
-- =============================================================

-- ---- 1. 新增字典类型 ----
INSERT INTO sys_dict_type (dict_type, dict_name, remark)
VALUES ('alert_level', '告警级别', '告警规则严重程度，一级最高、四级最低')
ON CONFLICT (dict_type) DO NOTHING;

-- ---- 2. 新增字典项 ----
INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort) VALUES
  ('alert_level', 'level_1', '一级', 'danger',  1),
  ('alert_level', 'level_2', '二级', 'warning', 2),
  ('alert_level', 'level_3', '三级', 'primary', 3),
  ('alert_level', 'level_4', '四级', 'info',    4);

-- ---- 3. 迁移 alert_rule.rule_level ----
UPDATE alert_rule SET rule_level = 'level_1' WHERE rule_level = 'fatal';
UPDATE alert_rule SET rule_level = 'level_2' WHERE rule_level = 'critical';
UPDATE alert_rule SET rule_level = 'level_3' WHERE rule_level = 'warning';
UPDATE alert_rule SET rule_level = 'level_4' WHERE rule_level = 'info';

-- ---- 4. 迁移 alert_event.rule_level ----
UPDATE alert_event SET rule_level = 'level_1' WHERE rule_level = 'fatal';
UPDATE alert_event SET rule_level = 'level_2' WHERE rule_level = 'critical';
UPDATE alert_event SET rule_level = 'level_3' WHERE rule_level = 'warning';
UPDATE alert_event SET rule_level = 'level_4' WHERE rule_level = 'info';
