-- =============================================================
-- V62：alert_rule 的 db_type / db_version 由字符串改为 FK ID
--
-- 背景：
--   V60 新增了 db_type VARCHAR(50)（存数据库类型 label，如 'MySQL'）。
--   V61 新增了 db_version VARCHAR(100)（存逗号分隔版本编码，如 '5.7,8.0'）。
--   本迁移将两列替换为规范化的外键，消除字符串冗余：
--     db_type_id   BIGINT → database_type.id
--     db_version_ids JSONB  → [database_version.id, ...] 数组
--
-- 迁移步骤：
--   1. 新增 db_type_id / db_version_ids 列
--   2. 回填数据（基于旧列内容查找对应 ID）
--   3. 删除旧字符串列 db_type / db_version
-- =============================================================

-- ---- 1. 新增目标列 ----
ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS db_type_id     BIGINT
        REFERENCES database_type (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS db_version_ids JSONB;

COMMENT ON COLUMN alert_rule.db_type_id IS
    '适用数据库类型 FK（database_type.id），内置规则必填，自定义规则可空';
COMMENT ON COLUMN alert_rule.db_version_ids IS
    '适用版本 ID 数组（database_version.id），NULL 表示适用该类型所有版本';

-- ---- 2. 回填 db_type_id ----
-- 通过旧 db_type 字符串（label）关联 database_type 表（大小写精确匹配）
UPDATE alert_rule ar
SET db_type_id = dt.id
FROM database_type dt
WHERE ar.db_type IS NOT NULL
  AND ar.db_type = dt.label;

-- ---- 3. 回填 db_version_ids（PL/pgSQL，逗号分隔编码 → ID 数组）----
DO $$
DECLARE
    r        RECORD;
    v_codes  TEXT[];
    v_code   TEXT;
    v_id     BIGINT;
    v_ids    JSONB;
    v_type   TEXT;
BEGIN
    FOR r IN
        SELECT id, db_version, db_type
        FROM alert_rule
        WHERE db_version IS NOT NULL
    LOOP
        -- database_version.db_type 使用小写（如 'mysql'）
        v_type  := lower(r.db_type);
        v_codes := string_to_array(r.db_version, ',');
        v_ids   := '[]'::JSONB;

        FOREACH v_code IN ARRAY v_codes LOOP
            v_code := trim(v_code);
            SELECT dv.id INTO v_id
            FROM database_version dv
            WHERE dv.db_type      = v_type
              AND dv.version_code = v_code
            LIMIT 1;

            IF v_id IS NOT NULL THEN
                v_ids := v_ids || to_jsonb(v_id);
            END IF;
        END LOOP;

        IF jsonb_array_length(v_ids) > 0 THEN
            UPDATE alert_rule SET db_version_ids = v_ids WHERE id = r.id;
        END IF;
    END LOOP;
END $$;

-- ---- 4. 删除旧字符串列 ----
ALTER TABLE alert_rule
    DROP COLUMN IF EXISTS db_type,
    DROP COLUMN IF EXISTS db_version;

-- ---- 5. 补充索引 ----
CREATE INDEX IF NOT EXISTS idx_alert_rule_db_type_id ON alert_rule (db_type_id);
