-- 采集任务列表与实例/主机历史日志改为数据库分页后，支持“任务键 + 最新时间”的索引。
-- host 日志固定 instance_id=0、host_id=实际主机 ID；实例日志 host_id IS NULL。
CREATE INDEX IF NOT EXISTS idx_collect_log_task_latest
    ON collect_log (instance_id, host_id, frequency, collect_time DESC);