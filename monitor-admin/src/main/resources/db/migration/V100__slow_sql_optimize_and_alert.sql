-- =============================================================
-- V100: 慢SQL分析——指纹优化状态 + 标记权限
--   1) 字典 slow_sql_optimize_status：未优化/已优化（状态统一走字典）
--   2) slow_sql_optimize_mark：指纹级优化状态人工标记表
--   3) slowsql 菜单补「标记优化」按钮权限，授予 dba/ops
-- =============================================================

-- ── 1. 优化状态字典 ────────────────────────────────────────────
INSERT INTO sys_dict_type (dict_type, dict_name, type, remark)
VALUES ('slow_sql_optimize_status', '慢SQL优化状态', 'system', 'SQL 指纹优化处理状态，由人工标记维护')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('slow_sql_optimize_status', 'unoptimized', '未优化', 'warning', 1),
  ('slow_sql_optimize_status', 'optimized',   '已优化', 'success', 2)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item i
  WHERE i.dict_type = v.dict_type AND i.item_value = v.item_value
);

-- ── 2. 指纹优化状态标记表 ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS slow_sql_optimize_mark (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instance_id BIGINT       NOT NULL,
    schema_name VARCHAR(128),
    digest      VARCHAR(128) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'unoptimized',  -- 字典 slow_sql_optimize_status
    updated_by  VARCHAR(50),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE slow_sql_optimize_mark IS 'SQL 指纹优化状态人工标记（无记录视为未优化）';
COMMENT ON COLUMN slow_sql_optimize_mark.status IS '优化状态：字典 slow_sql_optimize_status（unoptimized/optimized）';

-- schema_name 可空，用 COALESCE 表达式唯一索引保证 (实例, 库, 指纹) 唯一
CREATE UNIQUE INDEX IF NOT EXISTS uk_slow_sql_optimize_mark
    ON slow_sql_optimize_mark (instance_id, COALESCE(schema_name, ''), digest);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'trg_slow_sql_optimize_mark_updated_at'
    ) THEN
        CREATE TRIGGER trg_slow_sql_optimize_mark_updated_at
            BEFORE UPDATE ON slow_sql_optimize_mark
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END $$;

-- ── 3. 菜单按钮 + 角色权限 ─────────────────────────────────────
UPDATE sys_menu
   SET buttons = buttons || '[{"name": "标记优化", "code": "slowsql:mark", "status": "enabled"}]'::jsonb
 WHERE code = 'slowsql'
   AND NOT (buttons @> '[{"code": "slowsql:mark"}]'::jsonb);

UPDATE sys_role
   SET permissions = permissions || '["slowsql:mark"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["slowsql:mark"]'::jsonb);
