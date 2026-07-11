-- =============================================================
-- 基础管理模块建表：实例分组 / 菜单 / 操作日志
-- =============================================================

-- 实例分组（支持父子层级；实例通过 db_instance.group_ids 多分组归属）
CREATE TABLE instance_group (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    type        VARCHAR(32)  NOT NULL DEFAULT '业务系统',   -- 业务系统/环境/区域/其他
    parent_id   BIGINT,
    owner       VARCHAR(64),                                -- 负责人展示名
    member_ids  JSONB        NOT NULL DEFAULT '[]'::jsonb,  -- 小组成员用户ID集合
    description VARCHAR(255),
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE instance_group IS '实例分组（父子层级 + 负责人/成员，数据范围授权依据）';

-- 菜单（扁平 + 排序；用于菜单管理 CRUD，权限码 menu:action）
CREATE TABLE sys_menu (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    code        VARCHAR(64)  NOT NULL,                      -- 菜单编码，如 users
    type        VARCHAR(16)  NOT NULL DEFAULT '系统级',     -- 系统级/实例级
    icon        VARCHAR(64),
    route       VARCHAR(128),                               -- 前端路由 path
    perm        VARCHAR(64),                                -- 访问权限码 menu:action
    sort        INT          NOT NULL DEFAULT 0,
    status      VARCHAR(16)  NOT NULL DEFAULT 'enabled',    -- enabled/disabled
    description VARCHAR(255),
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_menu_code UNIQUE (code)
);
COMMENT ON TABLE sys_menu IS '菜单（菜单管理维护；动态路由下发另见 /auth/menus）';

-- 操作日志（@OperateLog AOP 落库；审计只读 + 导出）
CREATE TABLE sys_oper_log (
    id        BIGSERIAL     PRIMARY KEY,
    oper_time TIMESTAMP      NOT NULL DEFAULT now(),
    username  VARCHAR(64),
    module    VARCHAR(64),
    action    VARCHAR(32),
    target    VARCHAR(255),
    ip        VARCHAR(64),
    success   BOOLEAN        NOT NULL DEFAULT TRUE,
    detail    VARCHAR(1000)
);
COMMENT ON TABLE sys_oper_log IS '操作日志（用户写操作审计记录）';
CREATE INDEX idx_oper_log_time ON sys_oper_log (oper_time DESC);
CREATE INDEX idx_oper_log_user ON sys_oper_log (username);

-- 菜单种子（与 /auth/menus 下发的功能项对应；菜单管理可见可改）
INSERT INTO sys_menu (name, code, type, icon, route, perm, sort, status, description) VALUES
  ('实例管理', 'instances',      '系统级', 'Coin',        '/instances',  'instance:list',   1, 'enabled', '数据库实例管理'),
  ('实时概况', 'realtime',       '实例级', 'TrendCharts', '/realtime',   NULL,              2, 'enabled', '实例实时监控概况'),
  ('告警中心', 'alert',          '实例级', 'Bell',        '/alert',      NULL,              3, 'enabled', '告警事件中心'),
  ('慢查询',   'slowsql',        '实例级', 'Warning',     '/slowsql',    NULL,              4, 'enabled', '慢SQL分析'),
  ('报表',     'report',         '实例级', 'Document',    '/report',     NULL,              5, 'enabled', '巡检/事件报表'),
  ('用户管理', 'system_user',    '系统级', 'User',        '/system/user','system_user',    10, 'enabled', '系统用户管理'),
  ('角色管理', 'system_role',    '系统级', 'UserFilled',  '/system/role','system_role',    11, 'enabled', '角色与权限管理'),
  ('实例分组', 'system_group',   '系统级', 'FolderOpened','/system/group',NULL,            12, 'enabled', '实例分组管理'),
  ('菜单管理', 'system_menu',    '系统级', 'Menu',        '/system/menu','system_menu',    13, 'enabled', '菜单管理'),
  ('数据保留', 'data_retention', '系统级', 'Clock',       '/system/retention','data_retention',14,'enabled','数据保留策略'),
  ('操作日志', 'audit_log',      '系统级', 'List',        '/system/log', 'audit_log:view', 15, 'enabled', '操作审计日志');
