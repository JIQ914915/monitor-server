-- SQL Server 天级安全基线指标，为健康评分“安全配置”维度提供数据。
-- 仅执行只读元数据查询；缺少 VIEW ANY DEFINITION 时采集项明确降级且不产出零值。
INSERT INTO metric_definition
(metric_code, metric_name, db_type, domain, layer, value_type, unit,
 source_collector, process_type, frequency, description)
VALUES
('sqlserver.security.policy_disabled_login_count',
 '未启用密码策略的登录账号数', 'sqlserver', 'security', 'guard', 'numeric', 'count',
 'security', 'raw', '1d', '启用且未勾选 CHECK_POLICY 的 SQL 登录账号数量；需 VIEW ANY DEFINITION'),
('sqlserver.security.expiration_disabled_login_count',
 '未启用密码过期的登录账号数', 'sqlserver', 'security', 'analysis', 'numeric', 'count',
 'security', 'raw', '1d', '启用且未勾选 CHECK_EXPIRATION 的 SQL 登录账号数量；服务账号需结合组织策略人工核对'),
('sqlserver.security.sa_enabled',
 'sa 登录账号启用状态', 'sqlserver', 'security', 'guard', 'numeric', 'count',
 'security', 'raw', '1d', '内置 sa 登录账号是否启用，1=启用、0=禁用'),
('sqlserver.security.enabled_sysadmin_login_count',
 '启用的 sysadmin 成员数', 'sqlserver', 'security', 'analysis', 'numeric', 'count',
 'security', 'raw', '1d', 'sysadmin 固定服务器角色中当前启用的成员数量'),
('sqlserver.security.trustworthy_database_count',
 '启用 TRUSTWORTHY 的数据库数', 'sqlserver', 'security', 'guard', 'numeric', 'count',
 'security', 'raw', '1d', '用户数据库中 TRUSTWORTHY=ON 的数量，需核对跨数据库提权风险'),
('sqlserver.security.db_chaining_database_count',
 '启用跨库所有权链的数据库数', 'sqlserver', 'security', 'guard', 'numeric', 'count',
 'security', 'raw', '1d', '用户数据库中 DB_CHAINING=ON 的数量，需核对跨数据库权限边界')
ON CONFLICT (metric_code) DO NOTHING;