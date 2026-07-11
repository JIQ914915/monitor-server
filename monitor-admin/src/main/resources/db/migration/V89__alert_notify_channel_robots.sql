-- =============================================================
-- V89：告警通知通道扩展——钉钉 / 企业微信 / 飞书自定义机器人
-- =============================================================

ALTER TABLE alert_notify_record DROP CONSTRAINT IF EXISTS ck_alert_notify_record_channel;
ALTER TABLE alert_notify_record ADD CONSTRAINT ck_alert_notify_record_channel
    CHECK (channel IN ('webhook', 'email', 'sms', 'dingtalk', 'wecom', 'feishu'));

COMMENT ON COLUMN alert_notify_record.channel IS '通知通道：webhook/email/sms/dingtalk/wecom/feishu';
