-- 扩展 SQL Server 2012/2014/2016 兼容监控，并统一全部 SQL Server 版本排序。
INSERT INTO database_version (db_type_id, version_code, version_name, sort_order, description)
SELECT t.id, v.version_code, v.version_name, v.sort_order, v.description
  FROM database_type t
 CROSS JOIN (VALUES
    ('2012', 'SQL Server 2012', 1, '兼容监控；无 Query Store，Top SQL 自动降级为 DMV 累计快照'),
    ('2014', 'SQL Server 2014', 2, '兼容监控；无 Query Store，Top SQL 自动降级为 DMV 累计快照'),
    ('2016', 'SQL Server 2016', 3, '兼容监控；Query Store 可用但需在目标数据库启用'),
    ('2017', 'SQL Server 2017', 4, '兼容监控；Query Store 可用但需在目标数据库启用'),
    ('2019', 'SQL Server 2019', 5, '正式支持'),
    ('2022', 'SQL Server 2022', 6, '正式支持；使用 PERFORMANCE STATE 最小权限'),
    ('2025', 'SQL Server 2025', 7, '正式支持；使用 PERFORMANCE STATE 最小权限')
 ) AS v(version_code, version_name, sort_order, description)
 WHERE t.code = 'SQLSERVER'
ON CONFLICT (db_type_id, version_code) DO UPDATE
SET version_name = EXCLUDED.version_name,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description;

UPDATE database_type
   SET description = '支持 SQL Server 2012、2014、2016、2017、2019、2022、2025；'
       '2012/2014 无 Query Store 时自动降级为 DMV Top SQL；按版本和 Edition 探测能力；'
       '采集账号使用最小只读性能状态权限，不要求 sysadmin'
 WHERE code = 'SQLSERVER';
