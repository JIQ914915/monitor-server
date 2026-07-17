-- 数据库类型码值统一使用 database_type.code 的大写形式。
-- 展示名称只使用 database_type.label，不再参与持久化关联或服务端分派。
UPDATE database_type SET code = upper(btrim(code)) WHERE code <> upper(btrim(code));
UPDATE database_version SET db_type = upper(btrim(db_type)) WHERE db_type <> upper(btrim(db_type));
UPDATE metric_definition SET db_type = upper(btrim(db_type)) WHERE db_type <> upper(btrim(db_type));
UPDATE collector_config SET db_type = upper(btrim(db_type)) WHERE db_type <> upper(btrim(db_type));
UPDATE capability_matrix SET db_type = upper(btrim(db_type)) WHERE db_type <> upper(btrim(db_type));
UPDATE alert_drilldown_profile SET db_type = upper(btrim(db_type)) WHERE db_type <> upper(btrim(db_type));
