-- =============================================================
-- 表结构整改（七）：db_instance 的 db_type / version 规范化为外键 ID（需求 §21.2.2）
--   问题：db_type/version 以自由字符串存储，与 database_type / database_version 字典脱节，
--         大小写不一致（db_instance.db_type=MySQL、database_type.code=MYSQL、database_version.db_type=mysql）。
--   整改：新增 db_type_id -> database_type(id)、db_version_id -> database_version(id)，
--         回填后建立外键并丢弃旧字符串列；字段严禁再命名为 version。
-- =============================================================

-- ---- 1) 新增外键列（可空，便于回填与历史脏数据兜底）----
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS db_type_id    BIGINT;
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS db_version_id BIGINT;
COMMENT ON COLUMN db_instance.db_type_id    IS '数据库类型ID（外键 database_type.id）';
COMMENT ON COLUMN db_instance.db_version_id IS '数据库版本ID（外键 database_version.id）';

-- ---- 2) 回填：类型按 code/label 大小写不敏感匹配 ----
UPDATE db_instance i
   SET db_type_id = t.id
  FROM database_type t
 WHERE i.db_type_id IS NULL
   AND (lower(t.code) = lower(i.db_type) OR lower(t.label) = lower(i.db_type));

-- ---- 3) 回填：版本按 (类型, version_code) 匹配（database_version.db_type 为小写）----
UPDATE db_instance i
   SET db_version_id = v.id
  FROM database_version v
 WHERE i.db_version_id IS NULL
   AND lower(v.db_type) = lower(i.db_type)
   AND v.version_code = i.version;

-- ---- 4) 建立外键约束 ----
ALTER TABLE db_instance
    ADD CONSTRAINT fk_db_instance_type
    FOREIGN KEY (db_type_id) REFERENCES database_type (id);
ALTER TABLE db_instance
    ADD CONSTRAINT fk_db_instance_version
    FOREIGN KEY (db_version_id) REFERENCES database_version (id);

-- ---- 5) 索引：类型过滤走 db_type_id ----
CREATE INDEX IF NOT EXISTS idx_db_instance_type_id ON db_instance (db_type_id);

-- ---- 6) 丢弃旧字符串列（其上的 idx_db_instance_dbtype 会随列自动删除）----
ALTER TABLE db_instance DROP COLUMN IF EXISTS db_type;
ALTER TABLE db_instance DROP COLUMN IF EXISTS version;
