-- =============================================================
-- V87：告警通知发送记录与重试队列
-- =============================================================

CREATE TABLE IF NOT EXISTS alert_notify_record (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id         BIGINT,
    event_code       VARCHAR(50),
    rule_code        VARCHAR(100),
    notify_kind      VARCHAR(20)  NOT NULL,
    channel          VARCHAR(20)  NOT NULL,
    provider         VARCHAR(50),
    target           VARCHAR(500),
    payload          TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'pending',
    response_code    VARCHAR(50),
    response_body    TEXT,
    error_message    VARCHAR(1000),
    retry_count      INT          NOT NULL DEFAULT 0,
    max_retry        INT          NOT NULL DEFAULT 3,
    next_retry_time  TIMESTAMPTZ,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_notify_record_status CHECK (status IN ('pending', 'success', 'failed')),
    CONSTRAINT ck_alert_notify_record_channel CHECK (channel IN ('webhook', 'email', 'sms'))
);

COMMENT ON TABLE alert_notify_record IS '告警通知发送记录与失败重试队列';
COMMENT ON COLUMN alert_notify_record.notify_kind IS '通知类型：trigger/recovery';
COMMENT ON COLUMN alert_notify_record.channel IS '通知通道：webhook/email/sms';
COMMENT ON COLUMN alert_notify_record.provider IS '通道实现提供方，如 default/aliyun';
COMMENT ON COLUMN alert_notify_record.target IS '发送目标，如 URL/邮箱/手机号';
COMMENT ON COLUMN alert_notify_record.payload IS '发送载荷快照';
COMMENT ON COLUMN alert_notify_record.status IS '发送状态：pending/success/failed';

CREATE INDEX IF NOT EXISTS idx_alert_notify_record_retry
    ON alert_notify_record (status, next_retry_time, retry_count);

CREATE INDEX IF NOT EXISTS idx_alert_notify_record_event
    ON alert_notify_record (event_id, channel);

CREATE TRIGGER trg_alert_notify_record_updated_at
    BEFORE UPDATE ON alert_notify_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
