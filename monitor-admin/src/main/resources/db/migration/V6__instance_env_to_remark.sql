-- =============================================================
-- 实例表调整：去除环境(env)，新增备注(remark)
-- =============================================================
ALTER TABLE db_instance DROP COLUMN IF EXISTS env;
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS remark VARCHAR(255);
COMMENT ON COLUMN db_instance.remark IS '备注';
