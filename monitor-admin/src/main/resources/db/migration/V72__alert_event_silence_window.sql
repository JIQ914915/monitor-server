-- 告警事件静默窗口：窗口期内抑制同 dedup_key 的重复建单与通知
ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS silence_until_time TIMESTAMPTZ;

COMMENT ON COLUMN alert_event.silence_until_time IS '静默到期时间（窗口期内抑制同 dedup_key 重复建单与通知）';

CREATE INDEX IF NOT EXISTS idx_alert_event_dedup_silence_until
    ON alert_event (dedup_key, silence_until_time);
