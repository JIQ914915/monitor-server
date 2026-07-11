-- =============================================================
-- V68：告警级别口径统一为字典 alert_level
--
-- 说明：
--   1) 历史脚本中 rule_level 曾使用 fatal/critical/warning/info；
--   2) 自 V63 起已迁移到 level_1..level_4；
--   3) 本脚本做幂等兜底，确保增量环境或手工数据也统一为字典值。
-- =============================================================

-- 统一字段注释（文档口径）
COMMENT ON COLUMN alert_rule.rule_level IS
    '告警级别字典值（dict_type=alert_level）：level_1/level_2/level_3/level_4';

COMMENT ON COLUMN alert_event.rule_level IS
    '告警级别字典值（dict_type=alert_level）：level_1/level_2/level_3/level_4';

-- 幂等修正：规则级别
UPDATE alert_rule SET rule_level = 'level_1' WHERE rule_level = 'fatal';
UPDATE alert_rule SET rule_level = 'level_2' WHERE rule_level = 'critical';
UPDATE alert_rule SET rule_level = 'level_3' WHERE rule_level = 'warning';
UPDATE alert_rule SET rule_level = 'level_4' WHERE rule_level = 'info';

-- 幂等修正：事件级别
UPDATE alert_event SET rule_level = 'level_1' WHERE rule_level = 'fatal';
UPDATE alert_event SET rule_level = 'level_2' WHERE rule_level = 'critical';
UPDATE alert_event SET rule_level = 'level_3' WHERE rule_level = 'warning';
UPDATE alert_event SET rule_level = 'level_4' WHERE rule_level = 'info';

