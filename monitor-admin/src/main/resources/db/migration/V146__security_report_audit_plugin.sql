-- =============================================================
-- V146：安全专项报告 + 审计插件对接（差距分析 2026-07-08 第七轮）
--   1. 字典 report_type 新增 security（安全专项报告）
--   2. 审计插件对接指标（AuditPluginItem，天级，全版本）：
--      探测 MySQL Enterprise Audit / Percona audit_log / MariaDB
--      server_audit 的安装状态与关键配置，供"完整审计"能力判定
-- 注：全脚本幂等
-- =============================================================

-- ---- 1. 报告类型：安全专项 ----
INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT 'report_type', 'security', '安全专项', 'danger', 5
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND item_value = 'security'
);

-- ---- 2. 审计插件对接指标 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
SELECT * FROM (VALUES
('mysql.security.audit_plugin_active',
 '审计插件启用状态',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.audit_plugin', 'state', '1d',
 '数据库侧审计插件是否已安装并处于 ACTIVE（1=启用 0=未启用）；支持识别 MySQL Enterprise '
 || 'Audit / Percona Audit Log（audit_log）与 MariaDB Audit Plugin（server_audit）'),
('mysql.security.audit_plugin_info',
 '审计插件配置详情',
 'mysql', 'security', 'analysis', 'text', NULL,
 'mysql.audit_plugin', 'state', '1d',
 '审计插件详情 JSON：{plugin,status,vars:{...}}，vars 含策略/输出格式/日志文件等关键配置；'
 || '审计日志本体写在数据库服务器本地，平台不远程读取文件内容，页面展示状态与配置巡检结论')
) AS v(metric_code, metric_name, db_type, domain, layer, value_type, unit,
       source_collector, process_type, frequency, description)
WHERE NOT EXISTS (
  SELECT 1 FROM metric_definition m WHERE m.metric_code = v.metric_code
);
