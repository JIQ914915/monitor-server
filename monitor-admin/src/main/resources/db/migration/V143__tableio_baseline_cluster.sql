-- =============================================================
-- V143：二期第一梯队（差距分析 2026-07-08 第五轮）
--
-- 采集/计算侧配套（本版本代码变更）：
--   1. TableIoStatItem（新增，1h/1d，5.7/8.0）：
--      表 I/O 热点对象指标（tableio.*）+ 疑似未使用索引清单（1d 文本）
--   2. BaselineDetectJobHandler（新增 xxl-job，小时级）：
--      历史同小时基线 vs 最近 1 小时均值，产出偏离百分比 + 异常标记（写 1h 表）
--      ※ 需在 xxl-job 管理端新建任务 baselineDetectJobHandler，建议 CRON：10 0/1 * * * ?
--   3. 慢 SQL 指纹聚类：POST /slow-sql/clusters（纯查询，无新表）
-- =============================================================

-- ---- 1. 表 I/O 对象级指标（写 metric_capacity_object，object_type=table）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('tableio.read_count',
 '表读操作次数（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'count',
 'mysql.table_io_stat', 'delta', '1h',
 'performance_schema.table_io_waits_summary_by_table 差值：最近一小时该表的读操作（fetch）次数；'
 || '对象指标（object_type=table），每轮保留等待耗时 Top 20 的表。5.7/8.0 采集'),
('tableio.write_count',
 '表写操作次数（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'count',
 'mysql.table_io_stat', 'delta', '1h',
 '最近一小时该表的写操作（insert/update/delete）次数，对象指标'),
('tableio.wait_ms',
 '表I/O等待耗时（本周期）',
 'mysql', 'performance', 'analysis', 'numeric', 'ms',
 'mysql.table_io_stat', 'delta', '1h',
 '最近一小时该表的表级 I/O 等待累计耗时（毫秒）：等待事件大类之下的对象级下钻，'
 || '回答"时间花在哪张表上"；排序即热点表排名'),
('mysql.index.unused_list',
 '疑似未使用索引清单',
 'mysql', 'performance', 'analysis', 'text', NULL,
 'mysql.table_io_stat', 'state', '1d',
 'table_io_waits_summary_by_index_usage 中 COUNT_STAR=0 的二级索引（排除 PRIMARY 与系统库，最多 50 条）：'
 || '{"uptimeDays":N,"indexes":[{schema,table,index}]}；'
 || '计数自实例启动累计，uptimeDays 较小时结论不可靠，删除索引前须人工确认')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 基线学习与异常检测指标（小时级作业产出，写 metric_data_1h）----
INSERT INTO metric_definition
    (metric_code, metric_name, db_type, domain, layer, value_type, unit,
     source_collector, process_type, frequency, description)
VALUES
('mysql.baseline.qps_deviation_pct',
 'QPS基线偏离度',
 'mysql', 'performance', 'analysis', 'numeric', 'percent',
 'job.baseline_detect', 'raw', '1h',
 '最近 1 小时 QPS 均值相对历史基线（过去 7 天每天同一小时的均值）的偏离百分比：'
 || '正=偏高、负=偏低；样本不足 4 天或业务量过小（基线 QPS<1）时不产出'),
('mysql.baseline.qps_anomaly',
 'QPS偏离基线异常',
 'mysql', 'performance', 'guard', 'numeric', 'count',
 'job.baseline_detect', 'raw', '1h',
 'QPS 异常标记（1/0）：偏离幅度 ≥50% 且超出历史波动 3σ 时置 1；'
 || '突增可能是异常流量/爬虫/故障重试，突降可能是应用故障或入口异常'),
('mysql.baseline.conn_deviation_pct',
 '连接数基线偏离度',
 'mysql', 'connection', 'analysis', 'numeric', 'percent',
 'job.baseline_detect', 'raw', '1h',
 '最近 1 小时连接总数均值相对历史同小时基线的偏离百分比（基线连接数 <5 时不产出）'),
('mysql.baseline.conn_anomaly',
 '连接数偏离基线异常',
 'mysql', 'connection', 'guard', 'numeric', 'count',
 'job.baseline_detect', 'raw', '1h',
 '连接数异常标记（1/0）：判定口径同 QPS；'
 || '突增常见于连接池配置变更/连接泄漏/异常客户端')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 3. 内置规则：基线偏离异常（布尔，全版本）----
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description)
VALUES
(
    'QPS偏离历史基线',
    'builtin.baseline.qps_anomaly',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.baseline.qps_anomaly',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"最近 1 小时 QPS 偏离 7 天同时段基线 ≥ 50% 且超出历史波动 3σ 时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"QPS 回到基线波动范围内后自动恢复"}',
    NULL,
    60, 'SYSTEM_DEFAULT', 'system',
    '最近 1 小时 QPS 与过去 7 天同时段基线相比偏离超过 50% 且超出历史波动范围（3σ）。'
    '偏高排查：异常流量、爬虫、故障重试风暴；偏低排查：应用服务异常、入口流量中断。'
    '结合 mysql.baseline.qps_deviation_pct 查看偏离方向与幅度'
),
(
    '连接数偏离历史基线',
    'builtin.baseline.conn_anomaly',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    'mysql.baseline.conn_anomaly',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"最近 1 小时连接总数偏离 7 天同时段基线 ≥ 50% 且超出历史波动 3σ 时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"连接数回到基线波动范围内后自动恢复"}',
    NULL,
    60, 'SYSTEM_DEFAULT', 'system',
    '最近 1 小时连接总数与过去 7 天同时段基线相比显著偏离。'
    '偏高排查：连接池配置变更、连接泄漏、新上线应用；偏低排查：应用批量下线或网络隔离'
)
ON CONFLICT (rule_code) DO NOTHING;

INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN ('builtin.baseline.qps_anomaly', 'builtin.baseline.conn_anomaly')
ON CONFLICT (rule_id, metric_code) DO NOTHING;
