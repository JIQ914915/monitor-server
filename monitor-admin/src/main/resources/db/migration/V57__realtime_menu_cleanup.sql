-- =============================================================
-- 实时概况菜单整理：补全权限码 / 按钮权限点 / 角色授权
--
-- 页面入口：monitor/realtime（前端 views/monitor/realtime/index.vue）
-- 功能 Tab：性能监控 / 连接信息 / 资源使用 / 事件 & 风险 / 配置参数
-- 涉及按钮：监控采集开关（需权限）/ 导出配置
-- =============================================================

-- ── 1. 补全页面访问权限码（原为 NULL，现统一为 realtime:view）──────────
UPDATE sys_menu
   SET perm        = 'realtime:view',
       description = '实例实时监控概况（性能/连接/资源/事件/配置）'
 WHERE code = 'realtime';

-- ── 2. 菜单按钮权限点 ─────────────────────────────────────────────────
--   realtime:toggle_monitor  监控采集开关（Banner 区 Switch）
--   realtime:export_config   导出配置参数（配置 Tab 导出按钮）
UPDATE sys_menu
   SET buttons = '[
     {"name": "监控采集开关", "code": "realtime:toggle_monitor", "status": "enabled"},
     {"name": "导出配置",    "code": "realtime:export_config",   "status": "enabled"}
   ]'::jsonb
 WHERE code = 'realtime';

-- ── 3. 角色权限码同步 ─────────────────────────────────────────────────

-- super_admin：已拥有通配 *:*，无需单独添加

-- dba：授予完整实时概况权限（含开关和导出）
UPDATE sys_role
   SET permissions = permissions
       || '["realtime:view", "realtime:toggle_monitor", "realtime:export_config"]'::jsonb
 WHERE code = 'dba'
   AND NOT (permissions @> '["realtime:view"]'::jsonb);

-- ops：授予查看 + 监控采集开关（不授予配置导出）
UPDATE sys_role
   SET permissions = permissions
       || '["realtime:view", "realtime:toggle_monitor"]'::jsonb
 WHERE code = 'ops'
   AND NOT (permissions @> '["realtime:view"]'::jsonb);

-- auditor：仅授予查看权限
UPDATE sys_role
   SET permissions = permissions
       || '["realtime:view"]'::jsonb
 WHERE code = 'auditor'
   AND NOT (permissions @> '["realtime:view"]'::jsonb);
