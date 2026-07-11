-- 去除数据库版本表中无实际业务意义的字段
ALTER TABLE database_version
    DROP COLUMN IF EXISTS min_version,
    DROP COLUMN IF EXISTS max_version,
    DROP COLUMN IF EXISTS is_recommended,
    DROP COLUMN IF EXISTS is_deprecated,
    DROP COLUMN IF EXISTS eol_date;
