-- P0-3：CounterDeltaStore 持久化表
-- 存储每个（实例, 计数器名）组合的上一次采样快照，
-- 供采集器重启后恢复差值计算基线，防止重启时第一个采集周期产生全量误报。
CREATE TABLE IF NOT EXISTS counter_snapshot (
    instance_id  BIGINT  NOT NULL,
    counter_name TEXT    NOT NULL,
    sample_ts    BIGINT  NOT NULL,   -- epoch 毫秒
    sample_value BIGINT  NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (instance_id, counter_name)
);

COMMENT ON TABLE  counter_snapshot                IS '采集计数器差值快照，供 Collector 重启后恢复 delta 计算';
COMMENT ON COLUMN counter_snapshot.instance_id   IS '被监控实例 ID';
COMMENT ON COLUMN counter_snapshot.counter_name  IS '计数器名称（如 Com_select、Bytes_sent）';
COMMENT ON COLUMN counter_snapshot.sample_ts     IS '采样时间戳（epoch 毫秒）';
COMMENT ON COLUMN counter_snapshot.sample_value  IS '采样时计数器累计值';
COMMENT ON COLUMN counter_snapshot.updated_at    IS '快照最后写入时间';

-- 便于按 instance_id 清理已下线实例的历史快照
CREATE INDEX IF NOT EXISTS idx_counter_snapshot_instance ON counter_snapshot (instance_id);
