-- =============================================================
-- 表结构整改（六）：database_type 补齐动态建连元数据 + 新建 database_version（需求 §21.2.1）
--   采用追加式：不重命名既有列（code/label/supported_versions/collector_class/enabled 保留），
--   仅新增 driver_class/url_template/default_port/db_icon/sort_order/description，
--   使 testConnection / 动态建连 具备 url_template 支撑（当前为硬编码 switch）。
-- =============================================================

-- ---- database_type 追加字段 ----
ALTER TABLE database_type ADD COLUMN IF NOT EXISTS driver_class VARCHAR(200);
ALTER TABLE database_type ADD COLUMN IF NOT EXISTS url_template VARCHAR(500);
ALTER TABLE database_type ADD COLUMN IF NOT EXISTS default_port INT;
ALTER TABLE database_type ADD COLUMN IF NOT EXISTS db_icon      VARCHAR(100);
ALTER TABLE database_type ADD COLUMN IF NOT EXISTS sort_order   INT DEFAULT 0;
ALTER TABLE database_type ADD COLUMN IF NOT EXISTS description  TEXT;
COMMENT ON COLUMN database_type.url_template IS 'JDBC URL 模板，如 jdbc:mysql://{host}:{port}/{database}';

-- 回填 MySQL（V2 已登记 code=MYSQL）
UPDATE database_type
   SET driver_class = 'com.mysql.cj.jdbc.Driver',
       url_template = 'jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=utf8&useSSL=false',
       default_port = 3306,
       sort_order   = 1
 WHERE code = 'MYSQL' AND driver_class IS NULL;

-- ---- 数据库版本表 ----
CREATE TABLE database_version (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    db_type            VARCHAR(20) NOT NULL,               -- 数据库类型
    version_code       VARCHAR(20) NOT NULL,               -- 版本编码（5.6/5.7/8.0）
    version_name       VARCHAR(50) NOT NULL,               -- 版本名称（MySQL 5.6）
    min_version        VARCHAR(20),
    max_version        VARCHAR(20),
    is_recommended     BOOLEAN     NOT NULL DEFAULT FALSE,
    is_deprecated      BOOLEAN     NOT NULL DEFAULT FALSE,
    eol_date           DATE,
    supported_features JSONB,
    sort_order         INT         NOT NULL DEFAULT 0,
    description        TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_db_version UNIQUE (db_type, version_code)
);
COMMENT ON TABLE database_version IS '数据库版本配置表';
CREATE INDEX idx_database_version_db_type     ON database_version (db_type);
CREATE INDEX idx_database_version_recommended ON database_version (is_recommended);
CREATE TRIGGER trg_database_version_updated_at BEFORE UPDATE ON database_version
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO database_version (db_type, version_code, version_name, min_version, max_version, is_recommended, is_deprecated, eol_date, sort_order) VALUES
('mysql', '5.6', 'MySQL 5.6', '5.6.0', '5.6.99', FALSE, TRUE,  '2021-02-01', 1),
('mysql', '5.7', 'MySQL 5.7', '5.7.0', '5.7.99', TRUE,  FALSE, '2023-10-31', 2),
('mysql', '8.0', 'MySQL 8.0', '8.0.0', '8.0.99', TRUE,  FALSE, NULL,         3),
('mysql', '8.4', 'MySQL 8.4', '8.4.0', '8.4.99', FALSE, FALSE, NULL,         4)
ON CONFLICT (db_type, version_code) DO NOTHING;
