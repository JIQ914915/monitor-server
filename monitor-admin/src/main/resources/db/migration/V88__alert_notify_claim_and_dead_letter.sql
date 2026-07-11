-- =============================================================
-- V88：告警通知记录支持"发送中"认领态与"死信"终态
--   - sending：记录已被某节点原子认领，其他节点/线程不得重复发送；
--   - dead：重试次数耗尽仍失败的死信终态，不再重试，仅供排查与清理。
-- =============================================================

ALTER TABLE alert_notify_record DROP CONSTRAINT ck_alert_notify_record_status;
ALTER TABLE alert_notify_record ADD CONSTRAINT ck_alert_notify_record_status
    CHECK (status IN ('pending', 'sending', 'success', 'failed', 'dead'));

COMMENT ON COLUMN alert_notify_record.status IS
    '发送状态：pending待发/sending发送中(已认领)/success成功/failed待重试/dead重试耗尽死信';
