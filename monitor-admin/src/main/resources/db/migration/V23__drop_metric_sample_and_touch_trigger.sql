-- =============================================================
-- 表结构整改（一）：
--   1) 建立全局「修改即更新」触发器函数 set_updated_at()（§21.2 约定，PG 无 ON UPDATE CURRENT_TIMESTAMP）
--      —— 供后续所有含 updated_at 的新表统一绑定；存量表的迁移由后续脚本按表绑定。
--   2) 废弃死表 metric_sample：已被 metric_data_1m/1h/1d（V20）取代，无任何代码写入，
--      列名(metric/ts)与新表(metric_code/collect_time)不一致，属遗留残表，直接删除。
-- =============================================================

-- 1) 全局 updated_at 触发器函数（幂等：CREATE OR REPLACE）
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2) 删除死表 metric_sample（若为 Hypertable，DROP TABLE 会连同 chunk 一并删除）
DROP TABLE IF EXISTS metric_sample;
