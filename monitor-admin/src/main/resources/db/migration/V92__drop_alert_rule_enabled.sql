-- =============================================================
-- V92：移除 alert_rule.enabled 模板级开关
--   内置规则的启停一律由 alert_rule_instance_config.enabled（实例级）承载：
--   评估任务从不读取模板级 enabled（无实例配置记录即不评估），
--   保留该列只会造成"模板停用却仍在评估"的语义误导，故删除。
-- =============================================================

DROP INDEX IF EXISTS idx_alert_rule_enabled;

ALTER TABLE alert_rule DROP COLUMN IF EXISTS enabled;
