-- =============================================================
-- 表结构整改（五）：采集配置域（需求 §21.2.1 + §20.5.2）
--   collector_config    采集器配置（按频率分级、SQL、超时、权重、所需权限、降级）
--   instance_collector  实例-采集器关联（按实例开关、状态、最近采集/错误、连续失败）
--   capability_matrix   能力矩阵（菜单可用性判定：版本/角色/依赖采集器/权限/降级）
--   含 updated_at 的表绑定 set_updated_at() 触发器。
-- =============================================================

-- ---- 采集器配置 ----
CREATE TABLE collector_config (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collector_code       VARCHAR(100) NOT NULL,            -- 采集器编码，如 mysql.global_status
    collector_name       VARCHAR(100) NOT NULL,            -- 采集器名称
    db_type              VARCHAR(20)  NOT NULL,            -- 数据库类型
    min_version          VARCHAR(20),                      -- 最低支持版本
    category             VARCHAR(50),                      -- 分类（基础监控/性能分析/安全审计...）
    collect_frequency    VARCHAR(10)  NOT NULL,            -- 采集频率级别（1m/1h/1d）
    collect_sql          TEXT,                             -- 采集 SQL（可含变量；空表示由采集器动态选择）
    timeout              INT          NOT NULL DEFAULT 5,  -- 超时（秒）
    execution_weight     INT          NOT NULL DEFAULT 1,  -- 执行权重（1-10，越大越重）
    default_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    required_permissions JSONB,                            -- 所需权限
    fallback_collector   VARCHAR(100),                     -- 降级采集器编码
    description          TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_collector_config_code UNIQUE (collector_code)
);
COMMENT ON TABLE collector_config IS '采集器配置表';
CREATE INDEX idx_collector_config_db_type   ON collector_config (db_type);
CREATE INDEX idx_collector_config_category  ON collector_config (category);
CREATE INDEX idx_collector_config_frequency ON collector_config (collect_frequency);
CREATE INDEX idx_collector_config_enabled   ON collector_config (default_enabled);
CREATE TRIGGER trg_collector_config_updated_at BEFORE UPDATE ON collector_config
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- 实例-采集器关联 ----
CREATE TABLE instance_collector (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instance_id           BIGINT       NOT NULL,           -- 实例ID
    collector_code        VARCHAR(100) NOT NULL,           -- 采集器编码
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    collect_frequency     VARCHAR(10),                     -- 覆盖默认频率（可空）
    status                VARCHAR(20)  NOT NULL DEFAULT 'normal', -- normal/permission_denied/version_not_support/error
    last_collect_time     TIMESTAMPTZ,
    last_success_time     TIMESTAMPTZ,
    last_error_message    TEXT,
    continuous_fail_count INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_instance_collector UNIQUE (instance_id, collector_code)
);
COMMENT ON TABLE instance_collector IS '实例采集器关联表';
CREATE INDEX idx_instance_collector_instance ON instance_collector (instance_id);
CREATE INDEX idx_instance_collector_status   ON instance_collector (status);
CREATE TRIGGER trg_instance_collector_updated_at BEFORE UPDATE ON instance_collector
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---- 能力矩阵 ----
CREATE TABLE capability_matrix (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    capability_code      VARCHAR(50)  NOT NULL,            -- 能力代码
    capability_name      VARCHAR(100) NOT NULL,            -- 能力名称
    menu_group           VARCHAR(50),                      -- 菜单分组
    db_type              VARCHAR(20)  NOT NULL,            -- 数据库类型
    min_version          VARCHAR(20),                      -- 最低版本
    max_version          VARCHAR(20),                      -- 最高版本
    applicable_roles     JSONB,                            -- 适用角色（master/slave/standalone...）
    required_collectors  JSONB,                            -- 依赖的 Collector
    required_permissions JSONB,                            -- 需要的权限
    required_extensions  JSONB,                            -- 需要的扩展（PostgreSQL）
    fallback_config      JSONB,                            -- 降级配置
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_capability_dbtype UNIQUE (capability_code, db_type)
);
COMMENT ON TABLE capability_matrix IS '能力矩阵配置表';
CREATE INDEX idx_capability_matrix_db_type    ON capability_matrix (db_type);
CREATE INDEX idx_capability_matrix_capability ON capability_matrix (capability_code);

-- ---- 种子：MySQL 采集器（按频率分级，与 §21.2.1 一致） ----
INSERT INTO collector_config (collector_code, collector_name, db_type, min_version, category, collect_frequency, collect_sql, timeout, execution_weight, default_enabled, required_permissions) VALUES
('mysql.global_status',      '全局状态',   'mysql', '5.6', '基础监控', '1m', 'SHOW GLOBAL STATUS', 5, 2, TRUE, '[]'::jsonb),
('mysql.processlist',        '连接列表',   'mysql', '5.6', '基础监控', '1m', 'SELECT * FROM information_schema.processlist WHERE (command <> ''Sleep'' OR time > 60)', 5, 3, TRUE, '["PROCESS"]'::jsonb),
('mysql.innodb_trx',         'InnoDB事务', 'mysql', '5.6', '基础监控', '1m', 'SELECT * FROM information_schema.innodb_trx', 5, 2, TRUE, '["PROCESS"]'::jsonb),
('mysql.replica_status',     '复制状态',   'mysql', '5.6', '高可用监控', '1m', NULL, 5, 2, TRUE, '["REPLICATION CLIENT","SELECT:performance_schema"]'::jsonb),
('mysql.performance_schema.statement_digest', 'SQL性能摘要', 'mysql', '5.7', '性能分析', '1h', 'SELECT * FROM performance_schema.events_statements_summary_by_digest ORDER BY SUM_TIMER_WAIT DESC LIMIT 200', 10, 5, TRUE, '["SELECT:performance_schema"]'::jsonb),
('mysql.global_variables',   '全局变量',   'mysql', '5.6', '基础监控', '1d', 'SHOW GLOBAL VARIABLES', 10, 3, TRUE, '[]'::jsonb),
('mysql.information_schema.tables', '表容量', 'mysql', '5.6', '容量分析', '1h', 'SELECT TABLE_SCHEMA, TABLE_NAME, ENGINE, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH FROM information_schema.tables WHERE TABLE_SCHEMA NOT IN (''mysql'',''information_schema'',''performance_schema'',''sys'')', 300, 8, TRUE, '["SELECT:业务库"]'::jsonb),
('mysql.security.user',      '账号安全',   'mysql', '5.6', '安全审计', '1d', 'SELECT user, host, password_expired, password_lifetime, account_locked FROM mysql.user', 30, 4, TRUE, '["SELECT:mysql.user"]'::jsonb)
ON CONFLICT (collector_code) DO NOTHING;

-- ---- 种子：能力矩阵（MySQL Top SQL / 复制监控） ----
INSERT INTO capability_matrix (capability_code, capability_name, menu_group, db_type, min_version, max_version, applicable_roles, required_collectors, required_permissions, required_extensions, fallback_config, enabled) VALUES
('top_sql', 'Top SQL 分析', 'performance', 'mysql', '5.7', NULL, '["master","slave","standalone"]'::jsonb, '["mysql.performance_schema.statement_digest"]'::jsonb, '["PROCESS"]'::jsonb, NULL, '{"mode":"fallback","collector":"mysql.slow_log","limitation":"仅支持慢查询日志"}'::jsonb, TRUE),
('replication', '复制监控', 'ha', 'mysql', '5.6', NULL, '["master","slave"]'::jsonb, '["mysql.replica_status"]'::jsonb, '["REPLICATION CLIENT"]'::jsonb, NULL, NULL, TRUE)
ON CONFLICT (capability_code, db_type) DO NOTHING;
