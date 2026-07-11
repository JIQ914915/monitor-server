-- =============================================================
-- 示例实例状态多样化：补充 告警(warning)/故障(error) 状态，便于演示实例列表状态展示
-- status：online 在线 / warning 告警 / error 故障 / offline 离线
-- =============================================================
UPDATE db_instance SET status = 'warning', health = 68 WHERE name = 'mysql-test-02';
UPDATE db_instance SET status = 'error',   health = 56 WHERE name = 'mysql-prod-03';
