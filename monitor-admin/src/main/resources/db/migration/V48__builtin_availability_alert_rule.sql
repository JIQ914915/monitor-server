-- =============================================================
-- V48：内置可用性告警规则
--   builtin.availability：mysql.availability < 1 触发（实例不可达），
--   mysql.availability == 1 恢复。
--   rule_type = 'builtin' — 在告警规则管理页面通过 rule_type 过滤隐藏。
-- =============================================================

INSERT INTO alert_rule (
    rule_name,
    rule_code,
    rule_type,
    rule_level,
    metric_name,
    condition_config,
    recovery_config,
    scope_type,
    scope_config,
    notification_config,
    enabled,
    created_by
) VALUES (
    '实例不可达',
    'builtin.availability',
    'builtin',
    'critical',
    'mysql.availability',
    '{"operator":"<","threshold":1}',
    '{"operator":"==","threshold":1}',
    'all',
    NULL,
    NULL,
    TRUE,
    'system'
);
