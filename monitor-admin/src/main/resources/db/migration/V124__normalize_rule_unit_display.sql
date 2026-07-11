-- =============================================================
-- V124: 告警规则单位展示归一化
--   V114 等播种的规则 condition_config.unit 直接拷贝了指标定义的单位编码
--   （percent/count），前端与告警消息原样拼接，出现"大于等于20.0percent"。
--   归一：percent → '%'，count → ''（计数不带单位）；
--   同步订正实例级覆盖配置与历史事件文案。
-- 幂等：条件更新，重复执行无副作用。
-- =============================================================

-- ---- 规则模板 ----
UPDATE alert_rule
   SET condition_config = jsonb_set(condition_config, '{unit}', '"%"')
 WHERE condition_config->>'unit' = 'percent';

UPDATE alert_rule
   SET condition_config = jsonb_set(condition_config, '{unit}', '""')
 WHERE condition_config->>'unit' = 'count';

UPDATE alert_rule
   SET recovery_config = jsonb_set(recovery_config, '{unit}', '"%"')
 WHERE recovery_config->>'unit' = 'percent';

UPDATE alert_rule
   SET recovery_config = jsonb_set(recovery_config, '{unit}', '""')
 WHERE recovery_config->>'unit' = 'count';

-- ---- 实例级覆盖配置 ----
UPDATE alert_rule_instance_config
   SET condition_config = jsonb_set(condition_config, '{unit}', '"%"')
 WHERE condition_config->>'unit' = 'percent';

UPDATE alert_rule_instance_config
   SET condition_config = jsonb_set(condition_config, '{unit}', '""')
 WHERE condition_config->>'unit' = 'count';

UPDATE alert_rule_instance_config
   SET recovery_config = jsonb_set(recovery_config, '{unit}', '"%"')
 WHERE recovery_config->>'unit' = 'percent';

UPDATE alert_rule_instance_config
   SET recovery_config = jsonb_set(recovery_config, '{unit}', '""')
 WHERE recovery_config->>'unit' = 'count';

-- ---- 历史事件文案订正："20.0percent" → "20.0%"，"10.0count" → "10.0" ----
UPDATE alert_event
   SET alert_message = regexp_replace(alert_message, '([0-9])percent', '\1%', 'g')
 WHERE alert_message LIKE '%percent%';

UPDATE alert_event
   SET alert_message = regexp_replace(alert_message, '([0-9])count', '\1', 'g')
 WHERE alert_message ~ '[0-9]count';
