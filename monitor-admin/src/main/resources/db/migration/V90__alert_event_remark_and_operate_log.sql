-- =============================================================
-- V90：告警事件处置备注 + 操作流水表
-- =============================================================

ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS last_remark VARCHAR(500);
COMMENT ON COLUMN alert_event.last_remark IS '最近一次处置备注（confirm/handling/silence/close 时填写）';

CREATE TABLE IF NOT EXISTS alert_event_operate_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       BIGINT       NOT NULL,
    event_code     VARCHAR(50),
    operate_type   VARCHAR(20)  NOT NULL,
    from_status    VARCHAR(20)  NOT NULL,
    to_status      VARCHAR(20)  NOT NULL,
    operator_id    BIGINT,
    operator_name  VARCHAR(100),
    remark         VARCHAR(500),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_event_operate_log_type
        CHECK (operate_type IN ('confirm', 'handling', 'silence', 'close'))
);

COMMENT ON TABLE alert_event_operate_log IS '告警事件处置操作流水（人工流转历史，系统自动恢复/自愈不记录）';
COMMENT ON COLUMN alert_event_operate_log.operate_type IS '操作类型：confirm/handling/silence/close';
COMMENT ON COLUMN alert_event_operate_log.from_status IS '流转前状态';
COMMENT ON COLUMN alert_event_operate_log.to_status IS '流转后状态';
COMMENT ON COLUMN alert_event_operate_log.operator_name IS '操作人姓名快照，避免用户改名/注销后历史记录无法展示';

CREATE INDEX IF NOT EXISTS idx_alert_event_operate_log_event
    ON alert_event_operate_log (event_id, created_at DESC);

-- =============================================================
-- 字典类型增加范围字段：system 系统级 / custom 自定义
--   系统级字典及其字典项默认只读，仅 super_admin 可增删改
--   新增字典类型 dict_type_scope 供前端展示类型标签
-- =============================================================

ALTER TABLE sys_dict_type
    ADD COLUMN IF NOT EXISTS type VARCHAR(16) NOT NULL DEFAULT 'system';

COMMENT ON COLUMN sys_dict_type.type IS '字典范围：system 系统级 / custom 自定义（系统级仅超管可维护）';

-- 历史种子数据均视为系统级
UPDATE sys_dict_type SET type = 'system' WHERE type IS NULL OR type = '';

-- 新建字典类型默认自定义
ALTER TABLE sys_dict_type ALTER COLUMN type SET DEFAULT 'custom';

-- ---- 字典：字典类型范围 dict_type_scope ----
INSERT INTO sys_dict_type (dict_type, dict_name, type, remark)
VALUES ('dict_type_scope', '字典类型范围', 'system', '字典类型的系统级/自定义分类')
    ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
          ('dict_type_scope', 'system', '系统级', 'success', 1),
          ('dict_type_scope', 'custom', '自定义', 'info',    2)
     ) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_item i
    WHERE i.dict_type = v.dict_type AND i.item_value = v.item_value
);
