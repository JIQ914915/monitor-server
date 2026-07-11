-- =============================================================
-- V85：告警评估实例级互斥与持续窗口持久化
-- =============================================================

CREATE TABLE IF NOT EXISTS alert_evaluate_lock (
    instance_id       BIGINT      NOT NULL,
    lock_slot         BIGINT      NOT NULL,
    locked_by         VARCHAR(100) NOT NULL,
    lock_until_time   TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_alert_evaluate_lock PRIMARY KEY (instance_id, lock_slot)
);

COMMENT ON TABLE alert_evaluate_lock IS '告警评估实例级分布式锁，避免多 collector 重复评估同一实例';
COMMENT ON COLUMN alert_evaluate_lock.instance_id IS '实例ID';
COMMENT ON COLUMN alert_evaluate_lock.lock_slot IS '评估周期槽，当前使用 epoch minute';
COMMENT ON COLUMN alert_evaluate_lock.locked_by IS '持锁节点标识';
COMMENT ON COLUMN alert_evaluate_lock.lock_until_time IS '锁过期时间';

CREATE INDEX IF NOT EXISTS idx_alert_evaluate_lock_until
    ON alert_evaluate_lock (lock_until_time);

CREATE TRIGGER trg_alert_evaluate_lock_updated_at
    BEFORE UPDATE ON alert_evaluate_lock
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS alert_evaluate_window (
    dedup_key         VARCHAR(300) NOT NULL,
    window_type       VARCHAR(20)  NOT NULL,
    first_match_time  TIMESTAMPTZ  NOT NULL,
    last_eval_time    TIMESTAMPTZ  NOT NULL,
    expire_time       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_alert_evaluate_window PRIMARY KEY (dedup_key, window_type),
    CONSTRAINT ck_alert_evaluate_window_type CHECK (window_type IN ('trigger', 'recovery'))
);

COMMENT ON TABLE alert_evaluate_window IS '告警触发/恢复持续窗口状态，替代 collector 进程内内存状态';
COMMENT ON COLUMN alert_evaluate_window.dedup_key IS '告警事件归并键';
COMMENT ON COLUMN alert_evaluate_window.window_type IS '窗口类型：trigger/recovery';
COMMENT ON COLUMN alert_evaluate_window.first_match_time IS '首次满足窗口条件时间';
COMMENT ON COLUMN alert_evaluate_window.last_eval_time IS '最近一次评估时间';
COMMENT ON COLUMN alert_evaluate_window.expire_time IS '窗口状态过期时间，用于定期清理';

CREATE INDEX IF NOT EXISTS idx_alert_evaluate_window_expire
    ON alert_evaluate_window (expire_time);

CREATE TRIGGER trg_alert_evaluate_window_updated_at
    BEFORE UPDATE ON alert_evaluate_window
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
