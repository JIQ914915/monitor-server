-- V30: db_instance.group_ids 多分组归属为 JSONB 数组，
-- 按分组过滤/数据范围授权走 @> 包含查询；补 GIN 索引避免全表扫描。
-- 保持多分组模型（group_ids JSONB + 负责人A/B）不变，仅补索引以对齐需求文档 §21.2.2。
CREATE INDEX IF NOT EXISTS idx_db_instance_group_ids
    ON db_instance USING GIN (group_ids jsonb_path_ops);
