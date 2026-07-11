-- 下线升级通知：移除 notification_config 中 notifyOnEscalate 键
UPDATE alert_rule
SET notification_config = notification_config - 'notifyOnEscalate'
WHERE notification_config ? 'notifyOnEscalate';
