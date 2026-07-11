-- 告警通知发送跟踪：用于按规则 silencePeriod 控制重复通知频率
ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS last_notify_time TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS notify_count INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN alert_event.last_notify_time IS '最近一次通知发送时间';
COMMENT ON COLUMN alert_event.notify_count IS '通知发送次数';

CREATE INDEX IF NOT EXISTS idx_alert_event_last_notify_time
    ON alert_event (last_notify_time);
