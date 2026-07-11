-- =============================================================
-- V141：二期第一梯队指标补齐（差距分析 2026-07-08 第三轮）
--
-- 采集侧配套（本版本代码变更）：
--   1. WaitEventsItem（新增，1m，5.7/8.0）：等待事件大类耗时 + Top10 明细
--   2. GlobalStatusItem COUNTERS 增加 Handler_read_* / Select_*（速率）
--   3. ErrorLogItem（8.0）增加 Access denied 登录失败计数
--   4. InnodbStatusItem（新增，1m，全版本）：History list length + 最近死锁文本
-- =============================================================

-- ---- 1. 等待事件指标 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.waits.io_file_ms',
 '等待事件-文件IO（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'ms',
 'mysql.wait_events', 'delta', '1m',
 'performance_schema 等待事件 wait/io/file/* 大类在本采集周期内的累计等待时间（毫秒）；'
 || '偏高说明磁盘读写是主要瓶颈（数据文件/日志文件 IO）。5.7/8.0 采集，5.6 不支持'),
('mysql.waits.io_table_ms',
 '等待事件-表IO（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'ms',
 'mysql.wait_events', 'delta', '1m',
 'wait/io/table/* 大类本周期累计等待时间（毫秒）：行级读写入口耗时，'
 || '与 SQL 访问量正相关，突增时结合 Top SQL 分析'),
('mysql.waits.lock_ms',
 '等待事件-锁（本周期）',
 'mysql', 'lock', 'analysis', 'numeric', 'ms',
 'mysql.wait_events', 'delta', '1m',
 'wait/lock/* 大类本周期累计等待时间（毫秒）：表锁/元数据锁等待，'
 || '偏高说明存在 DDL 冲突或表级锁竞争'),
('mysql.waits.synch_ms',
 '等待事件-同步原语（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'ms',
 'mysql.wait_events', 'delta', '1m',
 'wait/synch/* 大类（mutex/rwlock）本周期累计等待时间（毫秒）；'
 || '该类仪器默认关闭，恒为 0 属正常，开启后偏高说明内部并发竞争激烈'),
('mysql.waits.other_ms',
 '等待事件-其他（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'ms',
 'mysql.wait_events', 'delta', '1m',
 '其余等待事件（socket 等）本周期累计等待时间（毫秒）'),
('mysql.waits.top_events',
 'Top 等待事件明细',
 'mysql', 'performance', 'analysis', 'text', NULL,
 'mysql.wait_events', 'state', '1m',
 '本采集周期耗时 Top 10 等待事件明细 JSON：[{event,count,timeMs,avgUs}]；'
 || '用于页面表格展示"数据库此刻主要在等什么"')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. Handler / Select 类型 / 网络流量速率 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.rate.Handler_read_key',
 'Handler索引读速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒基于索引键定位读取的行数（Handler_read_key 速率），越高说明索引利用越充分'),
('mysql.rate.Handler_read_next',
 'Handler索引顺序读速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒按索引顺序读取下一行的次数（范围扫描/索引扫描）'),
('mysql.rate.Handler_read_first',
 'Handler首行读速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒读取索引第一项的次数，偏高暗示全索引扫描较多'),
('mysql.rate.Handler_read_rnd',
 'Handler随机位置读速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒按固定位置读行的次数，偏高多见于需要排序后回表的查询'),
('mysql.rate.Handler_read_rnd_next',
 'Handler顺序扫描读速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒顺序读取下一行的次数（全表扫描的主要来源）；'
 || '与 Handler_read_key 的比值可评估整体索引使用效率'),
('mysql.rate.Select_scan',
 '全表扫描SELECT速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒对首个表做全表扫描的 SELECT 数（Select_scan 速率），持续偏高需补索引'),
('mysql.rate.Select_full_join',
 '无索引JOIN速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒未使用索引的 JOIN 次数（Select_full_join 速率），大于 0 即值得优化'),
('mysql.rate.Select_full_range_join',
 '范围JOIN速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒对被驱动表做范围扫描 JOIN 的次数'),
('mysql.rate.Select_range',
 '范围扫描SELECT速率',
 'mysql', 'sql', 'analysis', 'numeric', 'qps',
 'mysql.global_status', 'rate', '1m',
 '每秒对首个表做范围扫描的 SELECT 数（正常访问模式）'),
('mysql.rate.Bytes_received',
 '网络接收速率',
 'mysql', 'performance', 'analysis', 'numeric', 'bytes',
 'mysql.global_status', 'rate', '1m',
 '每秒从客户端接收的字节数（Bytes_received 速率），反映写入/请求流量'),
('mysql.rate.Bytes_sent',
 '网络发送速率',
 'mysql', 'performance', 'analysis', 'numeric', 'bytes',
 'mysql.global_status', 'rate', '1m',
 '每秒发送给客户端的字节数（Bytes_sent 速率），反映结果集流量；'
 || '突增常见于大结果集查询或全量导出')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. 登录失败与 InnoDB 状态解析 ----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.security.access_denied_count',
 '登录失败次数（近1小时）',
 'mysql', 'security', 'guard', 'numeric', 'count',
 'mysql.error_log', 'raw', '1h',
 '8.0 专属：过去 1 小时错误日志中 Access denied 条数；'
 || '与 mysql.delta.aborted_connects（全版本、分钟级）互补，'
 || '持续偏高可能存在口令扫描或配置错误的应用'),
('mysql.innodb.history_list_length',
 'Undo历史链长度',
 'mysql', 'performance', 'analysis', 'numeric', 'count',
 'mysql.innodb_status', 'raw', '1m',
 'SHOW ENGINE INNODB STATUS 中 History list length：待 purge 的 undo 记录数；'
 || '长事务会阻塞 purge 使其持续增长，导致空间膨胀与查询变慢（全版本通用）'),
('mysql.innodb.latest_deadlock',
 '最近死锁现场',
 'mysql', 'lock', 'analysis', 'text', NULL,
 'mysql.innodb_status', 'state', '1m',
 'SHOW ENGINE INNODB STATUS 的 LATEST DETECTED DEADLOCK 段全文（覆盖变更存储）：'
 || '包含死锁双方事务、持有/等待的锁与被回滚方，配合死锁频发告警定位根因')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 4. 内置规则：登录失败次数过多（8.0 错误日志口径）----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES (
    '登录失败次数过多（错误日志）',
    'builtin.access_denied.warning',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    (SELECT jsonb_agg(id ORDER BY id) FROM database_version
      WHERE db_type = 'mysql' AND version_code IN ('8.0', '8.4')),
    'mysql.security.access_denied_count',
    '{"operator":">=","threshold":20.0,"unit":"count"}',
    '{"operator":"<","threshold":5.0}',
    NULL,
    60, 'SYSTEM_DEFAULT', 'system',
    '过去 1 小时错误日志中 Access denied（账号/密码错误被拒绝）≥ 20 次，'
    '可能存在口令暴力破解或配置错误的应用，请核对来源 IP 与账号'
) ON CONFLICT (rule_code) DO NOTHING;

INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code = 'builtin.access_denied.warning'
ON CONFLICT (rule_id, metric_code) DO NOTHING;
