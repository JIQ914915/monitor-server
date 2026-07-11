-- =============================================================
-- 实例负责人 A/B 由展示名改为用户ID（owner_a/owner_b -> owner_a_id/owner_b_id）
-- 与分组负责人一致，作为数据范围授权依据；A 必填、B 可选
-- =============================================================
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS owner_a_id BIGINT;
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS owner_b_id BIGINT;
COMMENT ON COLUMN db_instance.owner_a_id IS '负责人A用户ID（关联 sys_user.id，必填）';
COMMENT ON COLUMN db_instance.owner_b_id IS '负责人B用户ID（关联 sys_user.id，可选）';

-- 存量数据：默认将负责人A指向内置管理员（id=1），避免历史行负责人为空
UPDATE db_instance SET owner_a_id = 1 WHERE owner_a_id IS NULL;

ALTER TABLE db_instance DROP COLUMN IF EXISTS owner_a;
ALTER TABLE db_instance DROP COLUMN IF EXISTS owner_b;
