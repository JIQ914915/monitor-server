-- =============================================================
-- V86：告警事件最近评估状态
-- =============================================================

ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS eval_state VARCHAR(30),
    ADD COLUMN IF NOT EXISTS eval_message VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_eval_time TIMESTAMPTZ;

COMMENT ON COLUMN alert_event.eval_state IS '最近评估状态：normal/metric_missing 等，不替代事件生命周期 status';
COMMENT ON COLUMN alert_event.eval_message IS '最近评估说明，如指标缺失原因';
COMMENT ON COLUMN alert_event.last_eval_time IS '最近一次规则评估时间';

CREATE INDEX IF NOT EXISTS idx_alert_event_eval_state
    ON alert_event (eval_state);
