-- 告警规则分类（category）字段废弃，改由字典 alert_level 统一管理级别
ALTER TABLE alert_rule DROP COLUMN IF EXISTS category;
