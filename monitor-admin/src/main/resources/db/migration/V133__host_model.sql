-- =============================================================
-- V133：主机指标监控（一）——主机模型
--   1. host 表：数据库主机登记（一期 exporter 拉取，SSH 字段预留二期）
--   2. db_instance.host_id：实例 → 所在主机关联（可空，存量不受影响）
--   3. collect_log.host_id：主机采集日志标记（instance_id 语义不变）
--   4. 字典：host_collect_mode / host_status
--   5. database_type 登记 HOST 内置类型（供 host.* 内置告警规则 db_type_id 引用，
--      无 url_template / collector_class，enabled=FALSE 使其不出现在实例类型下拉中）
-- =============================================================

-- ---- 1. host 表 ----
CREATE TABLE host (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_code       VARCHAR(64)  NOT NULL,                    -- 稳定业务编码（分片依据）
    name            VARCHAR(100) NOT NULL,
    ip              VARCHAR(64)  NOT NULL,
    os_type         VARCHAR(32)  NOT NULL DEFAULT 'linux',
    collect_mode    VARCHAR(16)  NOT NULL DEFAULT 'exporter', -- 字典 host_collect_mode
    exporter_port   INT          DEFAULT 9100,
    exporter_path   VARCHAR(64)  DEFAULT '/metrics',
    ssh_port        INT,                                       -- 二期 SSH 免 Agent 预留
    ssh_user        VARCHAR(64),
    ssh_password    VARCHAR(256),                              -- AES 加密（复用实例采集账号加密方式）
    remark          VARCHAR(255),
    status          VARCHAR(16)  NOT NULL DEFAULT 'normal',    -- 字典 host_status：normal/abnormal/paused
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_host_code UNIQUE (host_code)
);
COMMENT ON TABLE host IS '数据库主机登记表（主机指标采集配置，一期 node_exporter 拉取）';
COMMENT ON COLUMN host.collect_mode IS '采集方式：exporter=Exporter 拉取 / ssh=SSH 免 Agent（二期）/ none=不采集';
COMMENT ON COLUMN host.status IS '状态：normal 正常 / abnormal 异常（exporter 连续不可达）/ paused 已暂停';

CREATE INDEX idx_host_status ON host (status);

CREATE TRIGGER trg_host_updated_at BEFORE UPDATE ON host
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- 2. 实例关联主机 ----
ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS host_id BIGINT REFERENCES host(id);
COMMENT ON COLUMN db_instance.host_id IS '所在主机（host.id，可空）；关联后主机 host.* 指标扇出写入该实例';
CREATE INDEX IF NOT EXISTS idx_db_instance_host_id ON db_instance (host_id);

-- ---- 3. 采集日志标记主机 ----
ALTER TABLE collect_log ADD COLUMN IF NOT EXISTS host_id BIGINT;
COMMENT ON COLUMN collect_log.host_id IS '主机采集日志时填 host.id（此时 instance_id 填 0）；实例采集日志为 NULL';

-- ---- 4. 字典 ----
INSERT INTO sys_dict_type (dict_type, dict_name, type, remark) VALUES
  ('host_collect_mode', '主机采集方式', 'system', '主机指标采集通道：Exporter 拉取 / SSH 免 Agent / 不采集'),
  ('host_status',       '主机状态',     'system', '主机运行与采集状态')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  ('host_collect_mode', 'exporter', 'Exporter 拉取', 'primary', 1),
  ('host_collect_mode', 'ssh',      'SSH 免 Agent',  'warning', 2),
  ('host_collect_mode', 'none',     '不采集',        'info',    3),
  ('host_status', 'normal',   '正常',   'success', 1),
  ('host_status', 'abnormal', '异常',   'danger',  2),
  ('host_status', 'paused',   '已暂停', 'info',    3)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
   WHERE d.dict_type = v.dict_type AND d.item_value = v.item_value);

-- ---- 5. database_type 登记 HOST 内置类型 ----
--   仅作为 host.* 内置告警规则 db_type_id 的引用锚点；enabled=FALSE 避免出现在实例类型选择中。
INSERT INTO database_type (code, label, enabled, sort_order, description)
VALUES ('HOST', '主机', FALSE, 99, '内置类型：主机（OS）指标告警规则挂载用，不可作为实例数据库类型')
ON CONFLICT (code) DO NOTHING;
