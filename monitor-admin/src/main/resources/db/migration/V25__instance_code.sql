-- =============================================================
-- 表结构整改（三）：db_instance 新增稳定业务编码 instance_code（需求 §21.2.2 统一分片依据）
--   分片函数要求 abs(crc32(instance_code)) % shardTotal，以稳定且不复用的 instance_code
--   （而非自增 id）作哈希输入，删除/新增实例不影响既有实例的分片归属。
--   步骤：加列 → 回填 inst_{id} → 置 NOT NULL + UNIQUE。
-- =============================================================

ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS instance_code VARCHAR(50);
COMMENT ON COLUMN db_instance.instance_code IS '实例业务编码（稳定唯一，分片/引用依据）';

-- 回填存量：以 inst_{id} 生成稳定编码（仅对空值）
UPDATE db_instance SET instance_code = 'inst_' || id WHERE instance_code IS NULL;

ALTER TABLE db_instance ALTER COLUMN instance_code SET NOT NULL;

-- 唯一约束（幂等：存在则跳过）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_db_instance_code') THEN
        ALTER TABLE db_instance ADD CONSTRAINT uk_db_instance_code UNIQUE (instance_code);
    END IF;
END $$;
