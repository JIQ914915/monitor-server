-- =============================================================
-- 修复：sys_user_preference.update_time 建成了 TIMESTAMPTZ，
-- 而实体字段为 LocalDateTime，PostgreSQL 42.7 驱动无法把
-- TIMESTAMPTZ 直接转成 LocalDateTime，导致查询主题偏好报错。
-- 统一改回 TIMESTAMP（与其余管理表的 create_time/update_time 一致）。
-- =============================================================
ALTER TABLE sys_user_preference
    ALTER COLUMN update_time TYPE TIMESTAMP
    USING update_time AT TIME ZONE 'Asia/Shanghai';
