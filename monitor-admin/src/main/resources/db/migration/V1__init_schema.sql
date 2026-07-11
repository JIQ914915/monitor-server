-- =============================================================
-- 数据库监控平台 · 初始化建表（PostgreSQL 16）
-- 仅元数据/配置表；时序采样数据由 TimescaleDB Hypertable 承载（另见 §21.2）
-- =============================================================

-- 系统用户（多角色 roles，权限取并集 §5.5）
CREATE TABLE sys_user (
    id          BIGSERIAL    PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    nickname    VARCHAR(64),
    password    VARCHAR(100) NOT NULL,           -- BCrypt
    roles       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
COMMENT ON TABLE sys_user IS '系统用户';
COMMENT ON COLUMN sys_user.roles IS '角色编码集合（多角色，权限取并集）';

-- 角色（菜单与按钮权限码 menu:action 集合 §11.11.6）
CREATE TABLE sys_role (
    id          BIGSERIAL    PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    permissions JSONB        NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT uk_sys_role_code UNIQUE (code)
);
COMMENT ON TABLE sys_role IS '角色';
COMMENT ON COLUMN sys_role.permissions IS '权限码集合（menu:action）；super_admin 拥有通配 *:*';

-- 数据库实例（多分组 group_ids + 负责人A/B §6.1）
CREATE TABLE db_instance (
    id            BIGSERIAL   PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    host          VARCHAR(128) NOT NULL,
    port          INT          NOT NULL,
    db_type       VARCHAR(32)  NOT NULL,         -- MySQL/Oracle...
    version       VARCHAR(32)  NOT NULL,         -- 5.6/5.7/8.0
    env           VARCHAR(32),
    group_ids     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    owner_a       VARCHAR(64),
    owner_b       VARCHAR(64),
    conn_user     VARCHAR(128),
    conn_password VARCHAR(512),                  -- 加密存储 §13.3.2
    health        INT          NOT NULL DEFAULT 0,
    status        VARCHAR(16)  NOT NULL DEFAULT 'online',
    create_time   TIMESTAMP    NOT NULL DEFAULT now(),
    update_time   TIMESTAMP    NOT NULL DEFAULT now()
);
COMMENT ON TABLE db_instance IS '数据库实例';

-- 数据库类型登记（扩展性核心 §5.8）
CREATE TABLE database_type (
    id                 BIGSERIAL   PRIMARY KEY,
    code               VARCHAR(32) NOT NULL,     -- MYSQL...
    label              VARCHAR(64) NOT NULL,
    supported_versions VARCHAR(128),             -- 5.6,5.7,8.0
    collector_class    VARCHAR(255),
    enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_database_type_code UNIQUE (code)
);
COMMENT ON TABLE database_type IS '数据库类型登记表（新增类型在此登记 + 新增采集模块）';

-- 数据保留策略（系统级、6 类、仅管理员可写 §12.2）
CREATE TABLE retention_config (
    id             BIGSERIAL   PRIMARY KEY,
    category       VARCHAR(32) NOT NULL,         -- minute/hourly/daily/event/log/report
    retention_days INT         NOT NULL,
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_retention_category UNIQUE (category)
);
COMMENT ON TABLE retention_config IS '数据保留策略（系统/类别级统一配置）';

CREATE INDEX idx_db_instance_status ON db_instance (status);
CREATE INDEX idx_db_instance_dbtype ON db_instance (db_type);
