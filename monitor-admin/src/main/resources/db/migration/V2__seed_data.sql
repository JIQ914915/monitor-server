-- =============================================================
-- 种子数据：预设角色 + 管理员账号 + 数据库类型 + 数据保留默认 + 示例实例
-- 登录：admin / 123456 （口令为 BCrypt，生产请首次登录后修改）
-- =============================================================

-- 预设角色（§5：管理员/DBA/运维/只读；权限码 menu:action，super_admin 拥有通配 *:*）
INSERT INTO sys_role (code, name, permissions) VALUES
  ('super_admin', '系统管理员', '["*:*"]'::jsonb),
  ('dba', 'DBA 用户', '["instance:list","alert_rule:view","alert_rule:add","alert_rule:edit","collector:view","collector:edit","knowledge:view","knowledge:edit"]'::jsonb),
  ('ops', '运维用户', '["instance:list","alert_rule:view","alert_rule:add","alert_rule:edit","collector:view","knowledge:view"]'::jsonb),
  ('auditor', '只读审计用户', '["instance:list","audit_log:view","knowledge:view"]'::jsonb);

-- 管理员账号（roles 多角色数组；admin 关联 super_admin → 权限并集为 *:*）
INSERT INTO sys_user (username, nickname, password, roles, enabled) VALUES
  ('admin', '超级管理员',
   '$2b$10$mnpRcGG7SlViqPl7U.WuTumRlklBCmEAEPiy.pUVnriRKUFQ6/tNe',
   '["super_admin"]'::jsonb, TRUE);

-- 数据库类型登记（首期仅 MySQL，支持 5.6/5.7/8.0）
INSERT INTO database_type (code, label, supported_versions, collector_class, enabled) VALUES
  ('MYSQL', 'MySQL', '5.6,5.7,8.0', 'com.lzzh.monitor.collector.mysql.MySqlCollector', TRUE);

-- 数据保留出厂默认（6 类，单位：天；§12.2）
INSERT INTO retention_config (category, retention_days, enabled) VALUES
  ('minute', 7,   TRUE),   -- 分钟级（原始与标准指标同保留期）
  ('hourly', 30,  TRUE),   -- 小时级汇总
  ('daily',  365, TRUE),   -- 天级汇总
  ('event',  365, TRUE),   -- 告警事件
  ('log',    180, TRUE),   -- 操作日志
  ('report', 365, TRUE);   -- 报告文件

-- 示例实例（便于联调真实后端时列表非空）
INSERT INTO db_instance (name, host, port, db_type, version, env, group_ids, owner_a, owner_b, conn_user, health, status) VALUES
  ('mysql-prod-01', '10.0.0.11', 3306, 'MySQL', '8.0', '生产', '[1]'::jsonb, '张伟', '陈静', 'monitor_ro', 92, 'online'),
  ('mysql-test-02', '10.0.0.21', 3306, 'MySQL', '5.7', '测试', '[2]'::jsonb, '李娜', '刘洋', 'monitor_ro', 78, 'online'),
  ('mysql-prod-03', '10.0.0.13', 3306, 'MySQL', '5.6', '生产', '[1]'::jsonb, '王强', '赵敏', 'monitor_ro', 56, 'online');
