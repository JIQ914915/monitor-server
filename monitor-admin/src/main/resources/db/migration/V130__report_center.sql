-- =============================================================
-- V130：报告中心（§11.9 巡检与报表 + §11.7 报告能力）
--   1. monitor_report      报告归档表（巡检/性能/告警三类，内容分段 JSONB 落库）
--   2. report_schedule     定时报告任务表（每日/每周/每月定时生成归档）
--   3. 字典：report_type / report_cycle / report_gen_mode /
--            report_frequency / report_time_range / report_status
--   4. 系统级菜单「报告中心」+ 隐藏预览页 + 预设角色权限
-- 注：全脚本幂等（IF NOT EXISTS / ON CONFLICT / 存在性守卫）
-- =============================================================

-- ---- 1. 报告归档表 ----
CREATE TABLE IF NOT EXISTS monitor_report (
    id            BIGSERIAL    PRIMARY KEY,
    report_code   VARCHAR(64)  NOT NULL UNIQUE,           -- 如 INSP-1720000000000
    title         VARCHAR(200) NOT NULL,
    report_type   VARCHAR(32)  NOT NULL,                  -- 字典 report_type：inspection/performance/alert
    cycle         VARCHAR(16),                            -- 字典 report_cycle：daily/weekly/monthly/special（仅巡检报告）
    scope_type    VARCHAR(16)  NOT NULL DEFAULT 'instance', -- instance/group/owner
    scope_text    VARCHAR(500),                           -- 范围描述快照（如“分组：核心业务”）
    instance_ids  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    time_range    VARCHAR(8)   NOT NULL DEFAULT '24h',    -- 字典 report_time_range：24h/7d/30d
    gen_mode      VARCHAR(16)  NOT NULL DEFAULT 'manual', -- 字典 report_gen_mode：manual/schedule
    status        VARCHAR(16)  NOT NULL DEFAULT 'archived', -- 字典 report_status
    content       JSONB        NOT NULL DEFAULT '{}'::jsonb, -- {"sections":[{title,type,...}]}
    created_by    VARCHAR(64),
    generate_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE monitor_report IS '报告归档（§11.9）：巡检/性能/告警三类报告，生成时按真实数据分段落库';
COMMENT ON COLUMN monitor_report.content IS '报告正文 {"sections":[{"title","type"(summary/table/list),"summary","kv":[{label,value}],"columns":[{key,label}],"rows":[{...}],"items":["…"]}]}';

CREATE INDEX IF NOT EXISTS idx_monitor_report_type_time ON monitor_report (report_type, generate_time DESC);

-- ---- 2. 定时报告任务表 ----
CREATE TABLE IF NOT EXISTS report_schedule (
    id            BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    report_type   VARCHAR(32)  NOT NULL,                  -- 字典 report_type
    cycle         VARCHAR(16),                            -- 字典 report_cycle（仅巡检报告）
    scope_type    VARCHAR(16)  NOT NULL DEFAULT 'instance',
    scope_text    VARCHAR(500),
    instance_ids  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    time_range    VARCHAR(8)   NOT NULL DEFAULT '24h',
    frequency     VARCHAR(16)  NOT NULL DEFAULT 'daily',  -- 字典 report_frequency：daily/weekly/monthly
    run_time      VARCHAR(5)   NOT NULL DEFAULT '08:30',  -- 执行时间 HH:mm
    next_run      TIMESTAMPTZ,                            -- 下次执行时间（调度扫描依据）
    last_run_time TIMESTAMPTZ,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by    VARCHAR(64),
    created_at    TIMESTAMPTZ  DEFAULT now(),
    updated_at    TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE report_schedule IS '定时报告任务（§11.9）：xxl-job reportGenerateJobHandler 扫描 next_run<=now 的启用任务生成报告归档';

-- ---- 3. 字典 ----
INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('report_type',       '报告类型',     '报告中心报告类型：巡检/性能分析/告警分析'),
  ('report_cycle',      '巡检周期',     '巡检报告周期：日检/周报/月报/专项'),
  ('report_gen_mode',   '报告生成方式', '手动生成 / 定时任务生成'),
  ('report_frequency',  '报告执行频率', '定时报告任务执行频率'),
  ('report_time_range', '报告时间范围', '报告统计的数据时间窗口'),
  ('report_status',     '报告状态',     '报告归档状态')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('report_type', 'inspection',  '巡检报告', 'primary', 1),
  ('report_type', 'performance', '性能分析', 'success', 2),
  ('report_type', 'alert',       '告警分析', 'warning', 3),
  ('report_cycle', 'daily',   '日检', 'primary', 1),
  ('report_cycle', 'weekly',  '周报', 'success', 2),
  ('report_cycle', 'monthly', '月报', 'warning', 3),
  ('report_cycle', 'special', '专项', 'info',    4),
  ('report_gen_mode', 'manual',   '手动', 'info',    1),
  ('report_gen_mode', 'schedule', '定时', 'success', 2),
  ('report_frequency', 'daily',   '每日',    'primary', 1),
  ('report_frequency', 'weekly',  '每周一',  'success', 2),
  ('report_frequency', 'monthly', '每月1日', 'warning', 3),
  ('report_time_range', '24h', '最近24小时', 'primary', 1),
  ('report_time_range', '7d',  '最近7天',    'success', 2),
  ('report_time_range', '30d', '最近30天',   'warning', 3),
  ('report_status', 'archived', '已归档', 'info', 1)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
  WHERE d.dict_type = v.dict_type AND d.item_value = v.item_value
);

-- ---- 4. 菜单：系统设置 → 报告中心（+ 隐藏预览页） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('报告中心', 'report_center', 'menu', '系统级', 'Document',
        'report', 'system/report',
        'report:view', 21, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'),
        '报告中心（§11.9）：巡检/性能/告警三类报告的生成、归档、预览、下载与定时任务管理',
        '[
          {"name": "生成报告", "code": "report:create",   "status": "enabled"},
          {"name": "删除",     "code": "report:delete",   "status": "enabled"},
          {"name": "定时任务", "code": "report:schedule", "status": "enabled"}
        ]'::jsonb)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, active_menu, parent_id, description)
VALUES ('报告预览', 'report_preview', 'menu', '系统级', NULL,
        'report-preview', 'system/report-preview',
        'report:view', 99, 'enabled', FALSE, '/system/report',
        (SELECT id FROM sys_menu WHERE code = 'system'),
        '报告预览页（隐藏路由）：分段渲染报告正文，支持打印与导出 Word')
ON CONFLICT (code) DO NOTHING;

-- ---- 5. 预设角色权限 ----
-- super_admin 已拥有 *:*，无需单独添加

-- dba / ops：完整报告能力（§5.5 事件下钻与报告 R/W）
UPDATE sys_role
   SET permissions = permissions
       || '["report:view", "report:create", "report:delete", "report:schedule"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["report:view"]'::jsonb);

-- auditor：仅查看
UPDATE sys_role
   SET permissions = permissions
       || '["report:view"]'::jsonb
 WHERE code = 'auditor'
   AND NOT (permissions @> '["report:view"]'::jsonb);
