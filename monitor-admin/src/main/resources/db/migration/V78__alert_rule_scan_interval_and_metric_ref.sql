-- =============================================================
-- V78：告警规则扫描间隔标准化 + 规则-指标关联
-- =============================================================

ALTER TABLE alert_rule
    ADD COLUMN IF NOT EXISTS scan_interval_min INT,
    ADD COLUMN IF NOT EXISTS scan_interval_source VARCHAR(16);

COMMENT ON COLUMN alert_rule.scan_interval_min IS '规则扫描间隔（分钟）';
COMMENT ON COLUMN alert_rule.scan_interval_source IS '扫描间隔来源：SYSTEM_DEFAULT/USER_OVERRIDE';

UPDATE alert_rule
SET scan_interval_min = 1
WHERE scan_interval_min IS NULL;

UPDATE alert_rule
SET scan_interval_source = 'SYSTEM_DEFAULT'
WHERE scan_interval_source IS NULL;

ALTER TABLE alert_rule
    ALTER COLUMN scan_interval_min SET NOT NULL,
    ALTER COLUMN scan_interval_source SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_alert_rule_scan_interval_min_positive'
    ) THEN
        ALTER TABLE alert_rule
            ADD CONSTRAINT ck_alert_rule_scan_interval_min_positive
            CHECK (scan_interval_min >= 1);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_alert_rule_scan_interval_min ON alert_rule (scan_interval_min);

CREATE TABLE IF NOT EXISTS alert_rule_metric_ref (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_id     BIGINT       NOT NULL REFERENCES alert_rule (id) ON DELETE CASCADE,
    metric_code VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_alert_rule_metric_ref UNIQUE (rule_id, metric_code)
);

COMMENT ON TABLE alert_rule_metric_ref IS '告警规则依赖指标关联表（支持多指标）';
COMMENT ON COLUMN alert_rule_metric_ref.rule_id IS '规则ID';
COMMENT ON COLUMN alert_rule_metric_ref.metric_code IS '指标编码（metric_definition.metric_code）';

CREATE INDEX IF NOT EXISTS idx_alert_rule_metric_ref_rule_id ON alert_rule_metric_ref (rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_rule_metric_ref_metric_code ON alert_rule_metric_ref (metric_code);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'trg_alert_rule_metric_ref_updated_at'
    ) THEN
        CREATE TRIGGER trg_alert_rule_metric_ref_updated_at
            BEFORE UPDATE ON alert_rule_metric_ref
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END $$;

INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, trim(ar.metric_name) AS metric_code
FROM alert_rule ar
WHERE ar.metric_name IS NOT NULL
  AND trim(ar.metric_name) <> ''
ON CONFLICT (rule_id, metric_code) DO NOTHING;

UPDATE alert_rule ar
SET scan_interval_min = GREATEST(
        1,
        CEIL(((ar.condition_config ->> 'sqlInterval')::numeric) / 60.0)::int
    ),
    scan_interval_source = 'USER_OVERRIDE'
WHERE ar.rule_type = 'custom'
  AND ar.condition_config ? 'sqlInterval'
  AND (ar.condition_config ->> 'sqlInterval') ~ '^[0-9]+(\.[0-9]+)?$';

WITH metric_freq AS (
    SELECT
        arr.rule_id,
        MAX(
            CASE
                WHEN md.frequency ~ '^[0-9]+m$' THEN (substring(md.frequency from '^[0-9]+'))::int
                WHEN md.frequency ~ '^[0-9]+h$' THEN (substring(md.frequency from '^[0-9]+'))::int * 60
                WHEN md.frequency ~ '^[0-9]+d$' THEN (substring(md.frequency from '^[0-9]+'))::int * 1440
                ELSE 1
            END
        ) AS metric_sampling_max_min
    FROM alert_rule_metric_ref arr
    LEFT JOIN metric_definition md ON md.metric_code = arr.metric_code
    GROUP BY arr.rule_id
),
min_allowed AS (
    SELECT
        ar.id AS rule_id,
        GREATEST(1, COALESCE(mf.metric_sampling_max_min, 1)) AS min_allowed_min
    FROM alert_rule ar
    LEFT JOIN metric_freq mf ON mf.rule_id = ar.id
)
UPDATE alert_rule ar
SET scan_interval_min = GREATEST(ar.scan_interval_min, ma.min_allowed_min)
FROM min_allowed ma
WHERE ar.id = ma.rule_id;
