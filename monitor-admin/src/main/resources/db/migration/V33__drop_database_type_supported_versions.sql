-- =============================================================
-- V33: 删除 database_type.supported_versions 冗余列
-- 该列（"5.6,5.7,8.0" 逗号串）自 V28 建立独立的 database_version 表后已冗余：
--   - 后端无业务逻辑读取（DatabaseTypeService 无实现体使用它）；
--   - 前端版本下拉使用前端常量 VERSION_MAP，不消费该列；
--   - 支持版本改由结构化的 database_version 表（每版本一行 + 生命周期/特性）维护。
-- 故予以删除；具体支持版本请查询 database_version。
-- =============================================================
ALTER TABLE database_type DROP COLUMN IF EXISTS supported_versions;
