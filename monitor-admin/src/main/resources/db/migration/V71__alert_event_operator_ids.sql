-- =============================================================
-- V71：告警事件处置执行人ID落库
--
-- 需求：
--   - 记录“确认/静默/关闭”的执行人用户ID，便于审计与追溯。
-- =============================================================

ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS confirm_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS silence_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS close_user_id BIGINT;

COMMENT ON COLUMN alert_event.confirm_user_id IS '确认执行人用户ID（status->confirmed）';
COMMENT ON COLUMN alert_event.silence_user_id IS '静默执行人用户ID（status->ignored）';
COMMENT ON COLUMN alert_event.close_user_id   IS '关闭执行人用户ID（status->closed）';

