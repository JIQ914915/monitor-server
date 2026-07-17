-- database_version 归属于 database_type，使用主键外键关联，不再重复保存类型 code。
ALTER TABLE database_version ADD COLUMN IF NOT EXISTS db_type_id BIGINT;

UPDATE database_version version
   SET db_type_id = type.id
  FROM database_type type
 WHERE version.db_type_id IS NULL
   AND upper(btrim(version.db_type)) = upper(btrim(type.code));

DO $$
DECLARE
    unmatched_count BIGINT;
BEGIN
    SELECT count(*)
      INTO unmatched_count
      FROM database_version
     WHERE db_type_id IS NULL;
    IF unmatched_count > 0 THEN
        RAISE EXCEPTION 'database_version 存在 % 条无法匹配 database_type.code 的记录，请先修复数据', unmatched_count;
    END IF;
END $$;

ALTER TABLE database_version ALTER COLUMN db_type_id SET NOT NULL;
ALTER TABLE database_version DROP CONSTRAINT IF EXISTS uk_db_version;
DROP INDEX IF EXISTS idx_database_version_db_type;
ALTER TABLE database_version
    ADD CONSTRAINT fk_database_version_type
        FOREIGN KEY (db_type_id) REFERENCES database_type(id) ON DELETE RESTRICT;
ALTER TABLE database_version
    ADD CONSTRAINT uk_database_version_type_code UNIQUE (db_type_id, version_code);
ALTER TABLE database_version DROP COLUMN db_type;

COMMENT ON COLUMN database_version.db_type_id IS '数据库类型ID（外键 database_type.id）';
