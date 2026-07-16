-- SQL Server 批次 A：类型、版本、能力快照与基础指标。
ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS sqlserver_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE db_instance
    ADD COLUMN IF NOT EXISTS sqlserver_capabilities_detected_at TIMESTAMPTZ;

INSERT INTO database_type (code, label, collector_class, driver_class, url_template,
                           default_port, sort_order, description, enabled)
VALUES ('SQLSERVER', 'SQL Server',
        'com.lzzh.monitor.collector.sqlserver.SqlServerCollector',
        'com.microsoft.sqlserver.jdbc.SQLServerDriver',
        'jdbc:sqlserver://{host}:{port};databaseName={database};encrypt=false;'
        'loginTimeout=3;socketTimeout=30000;applicationName=monitor-collector',
        1433, 3,
        '正式支持 SQL Server 2017、2019、2022、2025；按版本和 Edition 探测能力；'
        '采集账号使用最小只读性能状态权限，不要求 sysadmin', TRUE)
ON CONFLICT (code) DO UPDATE
SET label = EXCLUDED.label,
    collector_class = EXCLUDED.collector_class,
    driver_class = EXCLUDED.driver_class,
    url_template = EXCLUDED.url_template,
    default_port = EXCLUDED.default_port,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    enabled = TRUE;

INSERT INTO database_version
    (db_type, version_code, version_name, sort_order, description)
VALUES
    ('sqlserver', '2017', 'SQL Server 2017', 1, '扩展支持至 2027-10-12'),
    ('sqlserver', '2019', 'SQL Server 2019', 2, '扩展支持至 2030-01-08'),
    ('sqlserver', '2022', 'SQL Server 2022', 3, '主流支持至 2028-01-11'),
    ('sqlserver', '2025', 'SQL Server 2025', 4, '主流支持至 2031-01-06')
ON CONFLICT (db_type, version_code) DO UPDATE
SET version_name = EXCLUDED.version_name,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description;

INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
    ('sqlserver.availability', '实例可达性', 'sqlserver', 'availability', 'guard',
     'numeric', 'count', 'availability', 'raw', '1m', '1=实例可连接，0=连接失败'),
    ('sqlserver.uptime', '实例运行时长', 'sqlserver', 'availability', 'guard',
     'numeric', 'count', 'availability', 'raw', '1m', 'SQL Server 本次启动后的运行秒数'),
    ('sqlserver.capability.clustered', '是否集群实例', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'availability', 'state', '1m', '1=集群实例，0=非集群'),
    ('sqlserver.capability.always_on', 'Always On 是否启用', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'availability', 'state', '1m', '1=已启用，0=未启用'),
    ('sqlserver.identity.engine_edition', 'Engine Edition', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'availability', 'state', '1m', 'SERVERPROPERTY EngineEdition'),
    ('sqlserver.identity.product_version', '产品版本', 'sqlserver', 'capability', 'explain',
     'text', NULL, 'availability', 'state', '1m', 'SQL Server 产品 Build 版本'),
    ('sqlserver.identity.edition', '产品 Edition', 'sqlserver', 'capability', 'explain',
     'text', NULL, 'availability', 'state', '1m', 'SQL Server Edition'),
    ('sqlserver.query_store.actual_state', 'Query Store 实际状态', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'capability', 'state', '1d', 'Query Store actual_state'),
    ('sqlserver.query_store.desired_state', 'Query Store 期望状态', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'capability', 'state', '1d', 'Query Store desired_state'),
    ('sqlserver.query_store.readonly_reason', 'Query Store 只读原因', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'capability', 'state', '1d', 'Query Store readonly_reason'),
    ('sqlserver.query_store.current_size_mb', 'Query Store 当前容量', 'sqlserver', 'capacity', 'analysis',
     'numeric', 'mb', 'capability', 'raw', '1d', 'Query Store 当前存储容量'),
    ('sqlserver.query_store.max_size_mb', 'Query Store 最大容量', 'sqlserver', 'capacity', 'analysis',
     'numeric', 'mb', 'capability', 'raw', '1d', 'Query Store 最大存储容量'),
    ('sqlserver.query_store.capture_mode', 'Query Store 捕获模式', 'sqlserver', 'capability', 'explain',
     'numeric', 'count', 'capability', 'state', '1d', 'Query Store query_capture_mode')
ON CONFLICT (metric_code) DO NOTHING;
