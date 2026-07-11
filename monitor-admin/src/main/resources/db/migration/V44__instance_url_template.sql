-- =============================================================
-- db_instance 新增 database_name 字段：
--   存储实例连接目标的数据库名称（如 mydb），采集侧建连时拼入 URL
--   占位符替换：database_type.url_template 中的 {database} → 此字段值
-- =============================================================
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS database_name VARCHAR(128);
COMMENT ON COLUMN db_instance.database_name IS '连接目标数据库名（如 mydb），采集侧建连时替换 URL 模板中的 {database} 占位符，空则不指定';
