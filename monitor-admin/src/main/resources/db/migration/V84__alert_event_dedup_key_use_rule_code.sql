-- =============================================================
-- V84：告警事件 dedup_key 语义切换为 rule_code
-- 目标：dedup_key = rule_code + ':' + instance_id + [':' + dimension_key]
-- =============================================================

COMMENT ON COLUMN alert_event.dedup_key IS
    '归并键=rule_code+instance_id+dimension_key，用于活跃事件归并去重';

-- 回填可直接映射的历史数据（rule_id 能关联到 alert_rule.id 的记录）
UPDATE alert_event e
SET dedup_key = ar.rule_code || ':' || e.instance_id ||
                CASE
                    WHEN e.dimension_key IS NOT NULL AND btrim(e.dimension_key) <> ''
                        THEN ':' || e.dimension_key
                    ELSE ''
                END
FROM alert_rule ar
WHERE e.rule_id = ar.id
  AND e.dedup_key IS DISTINCT FROM (
        ar.rule_code || ':' || e.instance_id ||
        CASE
            WHEN e.dimension_key IS NOT NULL AND btrim(e.dimension_key) <> ''
                THEN ':' || e.dimension_key
            ELSE ''
        END
    );
