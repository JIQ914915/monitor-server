-- PostgreSQL 第二期：Query Analytics、SQL 回退、计划历史、Vacuum/Index Advisor。

CREATE TABLE IF NOT EXISTS pg_query_stat_history (
    instance_id BIGINT NOT NULL,
    database_name VARCHAR(128),
    user_name VARCHAR(128),
    query_id VARCHAR(64) NOT NULL,
    query_text TEXT,
    delta_calls BIGINT NOT NULL,
    delta_exec_time_ms DOUBLE PRECISION NOT NULL,
    delta_rows BIGINT NOT NULL,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    stats_reset TIMESTAMPTZ,
    deallocations BIGINT NOT NULL DEFAULT 0,
    collect_time TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pg_query_stat_history_main
    ON pg_query_stat_history(instance_id, query_id, collect_time DESC);
CREATE INDEX IF NOT EXISTS idx_pg_query_stat_history_filter
    ON pg_query_stat_history(instance_id, database_name, user_name, collect_time DESC);

CREATE TABLE IF NOT EXISTS pg_sql_regression_event (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    database_name VARCHAR(128),
    query_id VARCHAR(64) NOT NULL,
    query_text TEXT,
    regression_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'warning',
    baseline_from TIMESTAMPTZ NOT NULL,
    baseline_to TIMESTAMPTZ NOT NULL,
    current_from TIMESTAMPTZ NOT NULL,
    current_to TIMESTAMPTZ NOT NULL,
    baseline_value DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    change_ratio DOUBLE PRECISION,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    related_lock_event_id BIGINT,
    related_host_event_id BIGINT,
    config_change_ref VARCHAR(128),
    release_event_ref VARCHAR(128),
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(instance_id, query_id, regression_type, current_from)
);
CREATE INDEX IF NOT EXISTS idx_pg_sql_regression_event_instance
    ON pg_sql_regression_event(instance_id, detected_at DESC);

CREATE TABLE IF NOT EXISTS pg_plan_history (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    database_name VARCHAR(128) NOT NULL,
    query_id VARCHAR(64) NOT NULL,
    sql_hash VARCHAR(64) NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    query_text TEXT NOT NULL,
    plan_json JSONB NOT NULL,
    node_summary JSONB NOT NULL DEFAULT '[]'::jsonb,
    previous_plan_hash VARCHAR(64),
    plan_changed BOOLEAN NOT NULL DEFAULT FALSE,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(instance_id, database_name, query_id, plan_hash)
);
CREATE INDEX IF NOT EXISTS idx_pg_plan_history_query
    ON pg_plan_history(instance_id, database_name, query_id, captured_at DESC);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname='timescaledb') THEN
        PERFORM create_hypertable('pg_query_stat_history', 'collect_time', if_not_exists => TRUE);
    END IF;
END $$;

UPDATE sys_menu
   SET buttons = buttons || '[
     {"name":"查看 Query Analytics","code":"pg_query:view","status":"enabled"},
     {"name":"查看计划历史","code":"pg_plan:view","status":"enabled"},
     {"name":"采集安全执行计划","code":"pg_plan:capture","status":"enabled"}
   ]'::jsonb
 WHERE code = 'pg_slowsql'
   AND NOT (buttons @> '[{"code":"pg_query:view"}]'::jsonb);

UPDATE sys_menu
   SET buttons = buttons || '[{"name":"查看 PG 顾问","code":"pg_advisor:view","status":"enabled"}]'::jsonb
 WHERE code = 'pg_objects'
   AND NOT (buttons @> '[{"code":"pg_advisor:view"}]'::jsonb);

UPDATE sys_role SET permissions = permissions || '["pg_query:view","pg_plan:view","pg_advisor:view"]'::jsonb
 WHERE code IN ('dba','ops')
   AND NOT (permissions @> '["pg_query:view","pg_plan:view","pg_advisor:view"]'::jsonb);
UPDATE sys_role SET permissions = permissions || '["pg_plan:capture"]'::jsonb
 WHERE code = 'dba' AND NOT (permissions @> '["pg_plan:capture"]'::jsonb);