-- =============================================================
-- V115：场景管理（需求 §11.8 场景化监控）
--   1. monitor_scenario         场景模板表（多信号 AND/OR 复合诊断，首批全内置）
--   2. scenario_instance_config 实例级启停配置（对齐 alert_rule_instance_config 双层模型）
--   3. 字典 event_source        告警事件来源（rule/scenario/system）
--   4. 实例级菜单「场景管理」 + 预设角色权限
-- 注：全脚本幂等（IF NOT EXISTS / ON CONFLICT / 存在性守卫），
--     兼容曾以旧编号执行过本内容的开发库（V114 让位给死锁规则后顺延）。
-- =============================================================

-- ---- 1. 场景模板表 ----
CREATE TABLE IF NOT EXISTS monitor_scenario (
    id                    BIGSERIAL    PRIMARY KEY,
    scenario_code         VARCHAR(100) NOT NULL UNIQUE,     -- 如 scenario.connection_pool_exhaustion
    scenario_name         VARCHAR(100) NOT NULL,
    description           TEXT,                             -- 场景说明（区分根因/消除噪音的设计意图）
    severity              VARCHAR(20)  NOT NULL DEFAULT 'level_3', -- 字典 alert_level（level_1~level_4）
    db_type_id            BIGINT REFERENCES database_type(id),
    db_version_ids        JSONB,                            -- 适用版本 id 数组，NULL=全版本（同 alert_rule）
    condition_config      JSONB        NOT NULL,            -- 条件组树：{"logic","duration","children":[{type:condition|group,...}]}
    recovery_config       JSONB,                            -- 可选 {"duration":秒}：触发逻辑不满足持续 N 秒后恢复
    diagnosis_template    TEXT         NOT NULL,            -- 触发时默认诊断结论
    diagnosis_branches    JSONB,                            -- 可选分支结论 [{"when":["condCode",...],"text":"..."}]，按命中信号组合匹配
    knowledge_article_ids JSONB        NOT NULL DEFAULT '[]'::jsonb, -- 关联知识库文章 id 数组
    notification_config   JSONB,
    scan_interval_min     INT          NOT NULL DEFAULT 1,
    builtin               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  DEFAULT now(),
    updated_at            TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE monitor_scenario IS '监控场景模板（多信号 AND/OR 复合诊断，触发后生成综合告警事件，§11.8）';
COMMENT ON COLUMN monitor_scenario.condition_config IS '条件组树 JSON：logic=AND/OR，children 为 condition（threshold/rate_change）或嵌套 group；duration 为触发持续秒数';
COMMENT ON COLUMN monitor_scenario.diagnosis_branches IS '分支诊断结论：when 为命中信号 code 集合（全部命中时匹配），无匹配时用 diagnosis_template';

-- ---- 2. 实例级启停配置 ----
CREATE TABLE IF NOT EXISTS scenario_instance_config (
    id            BIGSERIAL   PRIMARY KEY,
    scenario_code VARCHAR(100) NOT NULL,
    instance_id   BIGINT      NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    trigger_count BIGINT      NOT NULL DEFAULT 0,          -- 该实例上此场景累计触发次数
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT uk_scenario_instance UNIQUE (scenario_code, instance_id)
);
COMMENT ON TABLE scenario_instance_config IS '场景实例级配置：场景默认停用，无配置记录或 enabled=false 即不评估（同内置告警规则模式）';
CREATE INDEX IF NOT EXISTS idx_scenario_cfg_instance ON scenario_instance_config (instance_id);

-- ---- 3. 字典：告警事件来源 ----
INSERT INTO sys_dict_type (dict_type, dict_name, type, remark) VALUES
  ('event_source', '告警事件来源', 'system', '告警事件来源：告警规则 / 场景综合诊断 / 系统事件')
ON CONFLICT (dict_type) DO NOTHING;

-- sys_dict_item 无 (dict_type, item_value) 唯一约束，用存在性守卫保证幂等
INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT 'event_source', v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('rule',     '告警规则', 'primary', 1),
  ('scenario', '综合事件', 'warning', 2),
  ('system',   '系统事件', 'info',    3)
) AS v(item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
   WHERE d.dict_type = 'event_source' AND d.item_value = v.item_value);

-- ---- 4. 实例级菜单：监控视图 → 场景管理（排在告警管理之后） ----
-- 菜单已存在时跳过排序腾位，避免重复执行时其他菜单 sort 被再次 +1
UPDATE sys_menu SET sort = sort + 1
 WHERE parent_id = (SELECT id FROM sys_menu WHERE code = 'monitor')
   AND sort > (SELECT sort FROM sys_menu WHERE code = 'alert')
   AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'scenario_mgmt');

INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('场景管理', 'scenario_mgmt', 'menu', '实例级', 'Grid',
        'scenario', 'monitor/scenario',
        'scenario_mgmt:view',
        (SELECT sort + 1 FROM sys_menu WHERE code = 'alert'),
        'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'monitor'),
        '场景化监控：多信号复合诊断场景的启停与实时信号状态查看，触发后生成综合告警事件',
        '[
          {"name": "启停场景", "code": "scenario_mgmt:toggle", "status": "enabled"}
        ]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ---- 5. 预设角色权限（参照 V96 做法） ----
-- super_admin 已拥有 *:*，无需单独添加

-- dba / ops：查看 + 启停
UPDATE sys_role
   SET permissions = permissions
       || '["scenario_mgmt:view", "scenario_mgmt:toggle"]'::jsonb
 WHERE code IN ('dba', 'ops')
   AND NOT (permissions @> '["scenario_mgmt:view"]'::jsonb);

-- auditor：仅查看
UPDATE sys_role
   SET permissions = permissions
       || '["scenario_mgmt:view"]'::jsonb
 WHERE code = 'auditor'
   AND NOT (permissions @> '["scenario_mgmt:view"]'::jsonb);
