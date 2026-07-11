-- =============================================================
-- 数据字典模块 + 实例状态收敛为三态
--   sys_dict_type: 字典类型（instance_status 等）
--   sys_dict_item: 字典项（值/标签/标签颜色/排序），供前端下拉与标签渲染
--   实例状态统一为 normal(正常)/abnormal(异常)/paused(暂停)：
--     online→normal、warning/error→abnormal、offline→paused
--   字典管理菜单归入「系统设置」分组
-- =============================================================

-- ---- 字典类型 ----
CREATE TABLE sys_dict_type (
    id          BIGSERIAL    PRIMARY KEY,
    dict_type   VARCHAR(64)  NOT NULL,                    -- 字典类型编码，如 instance_status
    dict_name   VARCHAR(64)  NOT NULL,                    -- 字典类型名称，如 实例状态
    status      VARCHAR(16)  NOT NULL DEFAULT 'enabled',  -- enabled/disabled
    remark      VARCHAR(255),
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_dict_type UNIQUE (dict_type)
);
COMMENT ON TABLE sys_dict_type IS '数据字典类型';

-- ---- 字典项 ----
CREATE TABLE sys_dict_item (
    id          BIGSERIAL    PRIMARY KEY,
    dict_type   VARCHAR(64)  NOT NULL,                    -- 所属字典类型编码
    item_value  VARCHAR(64)  NOT NULL,                    -- 字典值，如 normal
    item_label  VARCHAR(64)  NOT NULL,                    -- 展示标签，如 正常
    tag_type    VARCHAR(16),                              -- 标签颜色：success/warning/danger/info/primary
    sort        INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'enabled',
    remark      VARCHAR(255),
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE sys_dict_item IS '数据字典项（值/标签/颜色/排序）';
CREATE INDEX idx_dict_item_type ON sys_dict_item (dict_type, sort);

-- ---- 种子：实例状态 ----
INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('instance_status', '实例状态', '数据库实例采集状态');
INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort) VALUES
  ('instance_status', 'normal',   '正常', 'success', 1),
  ('instance_status', 'abnormal', '异常', 'danger',  2),
  ('instance_status', 'paused',   '暂停', 'info',    3);

-- ---- 迁移实例状态：四态 → 三态 ----
UPDATE db_instance SET status = 'normal'   WHERE status = 'online';
UPDATE db_instance SET status = 'abnormal' WHERE status IN ('warning', 'error');
UPDATE db_instance SET status = 'paused'   WHERE status = 'offline';
ALTER TABLE db_instance ALTER COLUMN status SET DEFAULT 'normal';

-- ---- 字典管理菜单（归入「系统设置」分组） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('字典管理', 'system_dict', 'menu', '系统级', 'Notebook', 'dict', 'system/dict', 'system_dict', 17, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'), '数据字典维护',
        '[{"name":"新增","code":"dict:create","status":"enabled"},{"name":"编辑","code":"dict:update","status":"enabled"},{"name":"删除","code":"dict:delete","status":"enabled"}]'::jsonb)
ON CONFLICT (code) DO NOTHING;
