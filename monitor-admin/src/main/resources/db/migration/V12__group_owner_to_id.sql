-- =============================================================
-- 分组负责人由展示名改为用户ID（owner -> owner_id），并作为必填数据范围授权依据
-- =============================================================
ALTER TABLE instance_group ADD COLUMN IF NOT EXISTS owner_id BIGINT;
ALTER TABLE instance_group DROP COLUMN IF EXISTS owner;
COMMENT ON COLUMN instance_group.owner_id IS '负责人用户ID（关联 sys_user.id）';
