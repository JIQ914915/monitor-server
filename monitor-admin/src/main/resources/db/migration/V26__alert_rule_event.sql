-- =============================================================
-- 表结构整改（四）：告警核心域 alert_rule / alert_event（需求 §21.2.2 + §11）
--   遵循 §21.2 约定：TIMESTAMPTZ + created_at/updated_at + set_updated_at() 触发器。
--   alert_event 以「活跃状态部分唯一索引」保证同一归并键(dedup_key)不重复建单。
-- =============================================================

-- ---- 告警规则 ----
CREATE TABLE alert_rule (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_name           VARCHAR(100) NOT NULL,             -- 规则名称
    rule_code           VARCHAR(50)  NOT NULL,             -- 规则编码
    rule_type           VARCHAR(20)  NOT NULL,             -- builtin/custom
    rule_level          VARCHAR(20)  NOT NULL,             -- info/warning/critical/fatal
    metric_name         VARCHAR(100),                      -- 指标编码/名称
    condition_config    JSONB        NOT NULL,             -- 条件配置（单指标/持续/环比/复合）
    recovery_config     JSONB,                             -- 恢复条件配置
    scope_type          VARCHAR(20),                       -- 作用范围：all/instance/tag/business
    scope_config        JSONB,                             -- 作用范围配置
    notification_config JSONB,                             -- 通知配置
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_alert_rule_code UNIQUE (rule_code)
);
COMMENT ON TABLE alert_rule IS '告警规则表';
CREATE INDEX idx_alert_rule_enabled   ON alert_rule (enabled);
CREATE INDEX idx_alert_rule_rule_type ON alert_rule (rule_type);
CREATE TRIGGER trg_alert_rule_updated_at BEFORE UPDATE ON alert_rule
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- 告警事件 ----
CREATE TABLE alert_event (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_code        VARCHAR(50)  NOT NULL,               -- 事件编码（建单时生成）
    rule_id           BIGINT       NOT NULL,               -- 规则ID
    rule_name         VARCHAR(100) NOT NULL,               -- 规则名称（冗余快照）
    rule_level        VARCHAR(20)  NOT NULL,               -- 规则级别
    instance_id       BIGINT       NOT NULL,               -- 实例ID
    instance_name     VARCHAR(100) NOT NULL,               -- 实例名称（冗余快照）
    trigger_value     VARCHAR(100),                        -- 触发值
    threshold_value   VARCHAR(100),                        -- 阈值
    dimension_key     VARCHAR(200),                        -- 对象维度键（SQL digest/表/复制通道/表空间；无对象维度为空）
    dedup_key         VARCHAR(300),                        -- 归并键=rule_id+instance_id+dimension_key
    trigger_count     INT          NOT NULL DEFAULT 1,     -- 归并后触发次数
    last_trigger_time TIMESTAMPTZ,                         -- 最近一次触发时间
    trigger_time      TIMESTAMPTZ  NOT NULL,               -- 首次触发时间
    recovery_time     TIMESTAMPTZ,                         -- 恢复时间
    status            VARCHAR(20)  NOT NULL DEFAULT 'pending', -- pending/confirmed/handling/recovered/closed/ignored
    assignee          VARCHAR(50),                         -- 处理人
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_alert_event_code UNIQUE (event_code)
);
COMMENT ON TABLE alert_event IS '告警事件表';
CREATE INDEX idx_alert_event_instance_status ON alert_event (instance_id, status);
CREATE INDEX idx_alert_event_rule_status     ON alert_event (rule_id, status);
CREATE INDEX idx_alert_event_trigger_time    ON alert_event (trigger_time);
CREATE INDEX idx_alert_event_status          ON alert_event (status);
-- 仅对未恢复(活跃)事件保证同一归并键唯一：同对象不重复建单，恢复/关闭后可再次建单
CREATE UNIQUE INDEX uk_alert_event_active_dedup ON alert_event (dedup_key)
    WHERE status IN ('pending', 'confirmed', 'handling');
CREATE TRIGGER trg_alert_event_updated_at BEFORE UPDATE ON alert_event
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
