-- =============================================================
-- V156：PostgreSQL 支持（二期 C+D）——深度采集指标 + 内置规则 + 场景 + 下钻画像
--   采集侧新增（monitor-collector-postgresql）：
--     pg_bloat       小时级：表膨胀/死元组（pg_stat_user_tables）
--     pg_xid         小时级：XID 回卷风险（pg_database）
--     pg_checkpoint  分钟级：检查点与 bgwriter 刷盘（pg_stat_bgwriter 差值）
--     pg_wal         分钟级：WAL 产出速率（14+ pg_stat_wal）与归档成败（pg_stat_archiver）
--     pg_repl_slots  分钟级：复制槽数量/失活/滞留 WAL（pg_replication_slots）
--     pg_vacuum      分钟级：运行中 vacuum 数与 xmin 水位（pg_stat_progress_vacuum）
-- 注：全脚本幂等。
-- =============================================================

-- ---- 1. 指标定义 ----
INSERT INTO metric_definition (metric_code, metric_name, db_type, domain, layer, value_type, unit, source_collector, process_type, frequency, description) VALUES
-- 膨胀与死元组（小时级）
('pg.bloat.dead_tup_total',     '死元组总数',           'postgresql', 'capacity',    'guard',    'numeric', 'count',   'pg.pg_bloat',      'raw',   '1h', '当前库全部用户表死元组估算总数，持续上涨说明 vacuum 跟不上写入'),
('pg.bloat.dead_pct_max',       '单表死元组占比最大值', 'postgresql', 'capacity',    'guard',    'numeric', 'percent', 'pg.pg_bloat',      'raw',   '1h', '活元组>1000 的表中死元组占比最大值，>40% 建议人工介入处理膨胀'),
('pg.bloat.tables_over_20pct',  '膨胀表数量',           'postgresql', 'capacity',    'analysis', 'numeric', 'count',   'pg.pg_bloat',      'raw',   '1h', '死元组占比超 20% 的用户表数量'),
('pg.bloat.top_tables',         'Top 膨胀表明细',       'postgresql', 'capacity',    'analysis', 'text',    NULL,      'pg.pg_bloat',      'raw',   '1h', 'Top5 膨胀表 JSON：[{"table":"public.orders","deadTup":12000,"deadPct":35.2,"lastVacuumAt":"..."}]'),
-- XID 回卷（小时级）
('pg.xid.age_max',              'XID 最大年龄',         'postgresql', 'availability','guard',    'numeric', 'count',   'pg.pg_xid',        'raw',   '1h', '全部库 datfrozenxid 最大年龄；达 autovacuum_freeze_max_age（默认 2 亿）触发强制 freeze'),
('pg.xid.wraparound_pct',       'XID 回卷消耗百分比',   'postgresql', 'availability','guard',    'numeric', 'percent', 'pg.pg_xid',        'ratio', '1h', '距 XID 强制停写上限（20 亿）的消耗百分比，耗尽后实例拒绝写入，属 PG 特有高危风险'),
-- 检查点与 bgwriter（分钟级）
('pg.ckpt.timed_delta',         '定时检查点次数',       'postgresql', 'performance', 'analysis', 'numeric', 'count',   'pg.pg_checkpoint', 'delta', '1m', '周期内由 checkpoint_timeout 定时触发的检查点次数（正常形态）'),
('pg.ckpt.req_delta',           '请求检查点次数',       'postgresql', 'performance', 'guard',    'numeric', 'count',   'pg.pg_checkpoint', 'delta', '1m', '周期内因 WAL 写满（max_wal_size）被迫触发的检查点次数，持续>0 说明 max_wal_size 偏小，检查点风暴造成 IO 抖动'),
('pg.bgwriter.buffers_checkpoint_rate', '检查点刷盘速率', 'postgresql', 'performance', 'analysis', 'numeric', 'qps', 'pg.pg_checkpoint', 'delta', '1m', '检查点途径刷脏页速率（页/秒）'),
('pg.bgwriter.buffers_clean_rate',      'bgwriter 刷盘速率', 'postgresql', 'performance', 'analysis', 'numeric', 'qps', 'pg.pg_checkpoint', 'delta', '1m', 'bgwriter 后台清理途径刷脏页速率（页/秒）'),
('pg.bgwriter.buffers_backend_rate',    '后端直刷速率',   'postgresql', 'performance', 'guard',   'numeric', 'qps', 'pg.pg_checkpoint', 'delta', '1m', '后端进程被迫自己刷脏页的速率（页/秒），占比高说明共享缓冲区吃紧或 bgwriter 跟不上'),
-- WAL 与归档（分钟级）
('pg.wal.write_rate',           'WAL 生成速率',         'postgresql', 'traffic',     'guard',    'numeric', 'bytes',   'pg.pg_wal',        'delta', '1m', 'WAL 生成速率（字节/秒，PG 14+ 提供；13 无 pg_stat_wal 视图不产出）'),
('pg.wal.archived_delta',       'WAL 归档成功次数',     'postgresql', 'availability','analysis', 'numeric', 'count',   'pg.pg_wal',        'delta', '1m', '周期内 WAL 段归档成功次数（未开 archive_mode 时恒为 0）'),
('pg.wal.archive_failed_delta', 'WAL 归档失败次数',     'postgresql', 'availability','guard',    'numeric', 'count',   'pg.pg_wal',        'delta', '1m', '周期内 WAL 段归档失败次数，持续失败会使 WAL 堆积撑爆磁盘'),
-- 复制槽（分钟级）
('pg.repl.slots_total',         '复制槽总数',           'postgresql', 'replication', 'analysis', 'numeric', 'count',   'pg.pg_repl_slots', 'raw',   '1m', 'pg_replication_slots 中的复制槽总数（物理+逻辑）'),
('pg.repl.slots_inactive',      '失活复制槽数',         'postgresql', 'replication', 'guard',    'numeric', 'count',   'pg.pg_repl_slots', 'raw',   '1m', '未被消费的复制槽数量；失活槽阻止 WAL 回收，是 WAL 撑爆磁盘的最常见原因之一'),
('pg.repl.slot_retained_bytes_max', '复制槽滞留 WAL 最大值', 'postgresql', 'replication', 'guard', 'numeric', 'bytes', 'pg.pg_repl_slots', 'raw', '1m', '单个复制槽滞留（未被消费）的 WAL 字节数最大值'),
-- vacuum 活动（分钟级）
('pg.vacuum.running',           '运行中 vacuum 数',     'postgresql', 'capacity',    'analysis', 'numeric', 'count',   'pg.pg_vacuum',     'raw',   '1m', '当前正在执行的 vacuum/autovacuum 进程数（pg_stat_progress_vacuum）'),
('pg.vacuum.xmin_horizon_seconds', 'vacuum 阻碍时长',   'postgresql', 'capacity',    'guard',    'numeric', 'count',   'pg.pg_vacuum',     'raw',   '1m', '持有 backend_xmin 的最老事务持续秒数：该事务不结束，其后产生的死元组无法被 vacuum 回收')
ON CONFLICT (metric_code) DO NOTHING;

-- ---- 2. 内置规则 ----

-- [1] XID 回卷风险（level_1）：消耗超 40% 说明 autovacuum freeze 长期跟不上
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG XID 回卷风险',
    'builtin.pg.xid.wraparound',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.xid.wraparound_pct',
    '{"operator":">=","threshold":40.0,"unit":"percent"}',
    '{"operator":"<","threshold":30.0}',
    NULL,
    5, 'SYSTEM_DEFAULT', 'system',
    'XID 消耗超过停写上限的 40%，说明 autovacuum freeze 长期跟不上（默认 10% 就应完成回收）；'
    '耗尽后实例将拒绝写入且只能单用户模式修复。请排查长事务/失活复制槽/未消费的 prepared 事务，'
    '并对高龄大表人工执行 VACUUM FREEZE', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [2] WAL 归档失败（level_1，布尔）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG WAL 归档失败',
    'builtin.pg.wal.archive_failed',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.wal.archive_failed_delta',
    '{"operator":">=","threshold":1.0,"conditionType":"boolean",'
    '"displayText":"最近一个采集周期内出现 WAL 归档失败时触发"}',
    '{"operator":"<","threshold":1.0,'
    '"displayText":"后续周期归档不再失败时自动恢复"}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    'WAL 归档命令执行失败。失败期间 WAL 段不会被回收，将持续堆积直至撑爆磁盘；'
    '请检查 archive_command 的目标存储（NFS/对象存储）可用性与权限', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [3] 复制槽滞留 WAL 过大（level_2，持续 10 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 复制槽滞留 WAL 过大',
    'builtin.pg.repl.slot_retention',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.repl.slot_retained_bytes_max',
    '{"operator":">=","threshold":10737418240.0,"duration":600,"unit":"bytes"}',
    '{"operator":"<","threshold":5368709120.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '单个复制槽滞留 WAL 超过 10GB 持续 10 分钟，通常是下游（从库/逻辑订阅/CDC）停止消费；'
    '滞留 WAL 不会被回收，请确认下游状态，确认废弃后由 DBA 删除复制槽', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [4] 表膨胀严重（level_3）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 表膨胀严重',
    'builtin.pg.bloat.dead_pct',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.bloat.dead_pct_max',
    '{"operator":">=","threshold":40.0,"unit":"percent"}',
    '{"operator":"<","threshold":20.0}',
    NULL,
    60, 'SYSTEM_DEFAULT', 'system',
    '存在死元组占比超 40% 的表（小时级检测），查询会扫描大量无效行版本导致变慢；'
    '请查看 Top 膨胀表明细，排查长事务钉住水位问题，低峰期人工 VACUUM，重度膨胀评估 pg_repack', TRUE
) ON CONFLICT (rule_code) DO NOTHING;

-- [5] 检查点风暴（level_3，持续 10 分钟）
INSERT INTO alert_rule (rule_name, rule_code, rule_level, db_type_id, db_version_ids,
    metric_name, condition_config, recovery_config, notification_config,
    scan_interval_min, scan_interval_source, created_by, description, recommended)
VALUES (
    'PG 检查点触发过频',
    'builtin.pg.ckpt.storm',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    'pg.ckpt.req_delta',
    '{"operator":">=","threshold":1.0,"duration":600,"unit":"count"}',
    '{"operator":"<","threshold":1.0}',
    NULL,
    1, 'SYSTEM_DEFAULT', 'system',
    '连续 10 分钟每分钟都发生「WAL 写满被迫触发」的请求检查点，说明 max_wal_size 相对写入量偏小，'
    '频繁检查点造成周期性 IO 抖动与全页写放大；建议评估调大 max_wal_size 与 checkpoint_timeout', FALSE
) ON CONFLICT (rule_code) DO NOTHING;

-- 指标关联
INSERT INTO alert_rule_metric_ref (rule_id, metric_code)
SELECT ar.id, ar.metric_name
  FROM alert_rule ar
 WHERE ar.rule_code IN (
     'builtin.pg.xid.wraparound',
     'builtin.pg.wal.archive_failed',
     'builtin.pg.repl.slot_retention',
     'builtin.pg.bloat.dead_pct',
     'builtin.pg.ckpt.storm'
 )
ON CONFLICT (rule_id, metric_code) DO NOTHING;

-- ---- 3. 场景 ----

-- [1] XID 回卷与膨胀风险（OR）：回卷消耗 / vacuum 被长事务阻碍任一命中
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_wraparound_risk',
    'PG 回卷与膨胀风险',
    'XID 回卷消耗与 vacuum 阻碍联合监控（任一命中即触发）：回卷消耗偏高说明 freeze 跟不上；'
    'vacuum 被长事务钉住则死元组和 XID 年龄都会持续累积。两者都指向「回收机制失效」这一根因',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"OR","duration":0,"children":[
       {"type":"condition","code":"xid_pct","name":"XID 回卷消耗","metricCode":"pg.xid.wraparound_pct",
        "condType":"threshold","operator":">=","threshold":30,"unit":"%","exprText":"≥ 30%"},
       {"type":"condition","code":"xmin_stuck","name":"vacuum 阻碍时长","metricCode":"pg.vacuum.xmin_horizon_seconds",
        "condType":"threshold","operator":">=","threshold":3600,"unit":"s","exprText":"≥ 3600s（1小时）"}
     ]}'::jsonb,
    '{"duration":600}'::jsonb,
    '数据回收机制（vacuum/freeze）出现失效信号：先定位并结束钉住水位的长事务，'
    '再检查失活复制槽与 prepared 事务，最后对高龄大表安排低峰期 VACUUM FREEZE',
    '[{"when":["xid_pct"],"text":"XID 消耗偏高：按 age(datfrozenxid) 找出高龄库表，确认 autovacuum 是否被参数或长事务限制"},
      {"when":["xmin_stuck"],"text":"vacuum 被长事务钉住：按 backend_xmin 非空且 xact_start 最早定位元凶事务，推动应用提交或回滚"}]'::jsonb,
    5, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [2] WAL 堆积风险（OR）：归档失败 / 复制槽滞留 / 失活槽任一命中
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_wal_pileup',
    'PG WAL 堆积风险',
    '归档失败、复制槽滞留、失活复制槽三个信号联合监控（任一命中即触发）。'
    '三者都会阻止 WAL 段回收，最终结果一致：pg_wal 目录持续膨胀直至磁盘写满、实例宕机',
    'level_1',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"OR","duration":120,"children":[
       {"type":"condition","code":"archive_failed","name":"归档失败次数","metricCode":"pg.wal.archive_failed_delta",
        "condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"> 0（本采集周期）"},
       {"type":"condition","code":"slot_retained","name":"复制槽滞留 WAL","metricCode":"pg.repl.slot_retained_bytes_max",
        "condType":"threshold","operator":">=","threshold":5368709120,"unit":"bytes","exprText":"≥ 5GB"},
       {"type":"condition","code":"slot_inactive","name":"失活复制槽数","metricCode":"pg.repl.slots_inactive",
        "condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"≥ 1"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '存在阻止 WAL 回收的因素，磁盘被 WAL 撑爆前必须处理：'
    '归档失败查 archive_command 目标存储；复制槽滞留查下游消费方状态，确认废弃后删除复制槽',
    '[{"when":["archive_failed"],"text":"归档失败：检查归档目标（NFS/对象存储）可用性、磁盘空间与权限，修复后 PG 会自动重试"},
      {"when":["slot_retained"],"text":"复制槽滞留：下游（从库/逻辑订阅/CDC 工具）停止消费，确认下游进程状态并恢复消费"},
      {"when":["slot_inactive"],"text":"存在失活复制槽：确认是否为已下线的从库/订阅遗留，确认废弃后 SELECT pg_drop_replication_slot(...) 清理（须 DBA 确认）"}]'::jsonb,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [3] 检查点与刷盘压力（AND）：请求检查点频发 + 后端被迫直刷同时出现
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_checkpoint_pressure',
    'PG 检查点与刷盘压力',
    '请求检查点频发与后端直刷速率同时偏高时触发（AND）：写入量超出 max_wal_size 与 shared_buffers 的承载，'
    '表现为周期性 IO 抖动、TPS 锯齿。单独偶发的请求检查点不触发，过滤批量导入等一次性噪声',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"AND","duration":300,"children":[
       {"type":"condition","code":"ckpt_req","name":"请求检查点次数","metricCode":"pg.ckpt.req_delta",
        "condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"> 0（本采集周期）"},
       {"type":"condition","code":"backend_flush","name":"后端直刷速率","metricCode":"pg.bgwriter.buffers_backend_rate",
        "condType":"threshold","operator":">=","threshold":500,"unit":"count","exprText":"≥ 500 页/秒"}
     ]}'::jsonb,
    '{"duration":600}'::jsonb,
    '写入压力超出当前 WAL/缓冲区配置的承载：建议评估调大 max_wal_size（减少请求检查点）、'
    '调大 shared_buffers 或 bgwriter 参数（减少后端直刷），配置调整须评估内存余量后在低峰执行',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [4] 临时文件压力（AND）：临时文件产生频繁且写入量大（复用一期 pg_stat_database 指标）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_temp_spill',
    'PG 临时文件压力',
    '临时文件产生次数与写入速率同时偏高时触发（AND）：排序/哈希/物化超过 work_mem 后落盘，'
    '既拖慢当次查询又抢占磁盘 IO。对应 MySQL 的「临时表压力」场景',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"AND","duration":300,"children":[
       {"type":"condition","code":"temp_files","name":"临时文件产生速率","metricCode":"pg.delta.temp_files",
        "condType":"threshold","operator":">=","threshold":5,"unit":"count","exprText":"≥ 5 个/周期"},
       {"type":"condition","code":"temp_bytes","name":"临时文件写入速率","metricCode":"pg.rate.temp_bytes",
        "condType":"threshold","operator":">=","threshold":5242880,"unit":"bytes","exprText":"≥ 5MB/s"}
     ]}'::jsonb,
    '{"duration":600}'::jsonb,
    '持续产生临时文件：优先定位落盘大户 SQL（有 pg_stat_statements 时看 temp_blks_written 排名，'
    '或开启 log_temp_files），先优化 SQL 补索引消除大排序，再评估对报表会话单独调 work_mem',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 4. 下钻画像：PG 维护类（膨胀/回卷/WAL/检查点） ----
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_maintenance', 'PG 维护类', 'postgresql',
$J$[
  {"matchType":"prefix","pattern":"pg.bloat."},
  {"matchType":"prefix","pattern":"pg.xid."},
  {"matchType":"prefix","pattern":"pg.vacuum."},
  {"matchType":"prefix","pattern":"pg.wal."},
  {"matchType":"prefix","pattern":"pg.ckpt."},
  {"matchType":"prefix","pattern":"pg.bgwriter."},
  {"matchType":"exact","pattern":"scenario.pg_wraparound_risk"},
  {"matchType":"exact","pattern":"scenario.pg_wal_pileup"},
  {"matchType":"exact","pattern":"scenario.pg_checkpoint_pressure"}
]$J$::jsonb,
$J$[
  {"code":"pg.xid.wraparound_pct","label":"XID 回卷消耗","unit":"%","color":"#E5484D"},
  {"code":"pg.bloat.dead_pct_max","label":"单表死元组占比最大值","unit":"%","color":"#E08600"},
  {"code":"pg.vacuum.xmin_horizon_seconds","label":"vacuum 阻碍时长","unit":"s","color":"#9B59B6"},
  {"code":"pg.repl.slot_retained_bytes_max","label":"复制槽滞留 WAL","unit":"B","color":"#6366F1"},
  {"code":"pg.wal.archive_failed_delta","label":"归档失败次数/分钟","unit":"","color":"#B91C1C"},
  {"code":"pg.ckpt.req_delta","label":"请求检查点次数/分钟","unit":"","color":"#0C7C97"}
]$J$::jsonb,
$J$[
  {"cause":"长事务钉住 vacuum 水位（回收机制失效）","confidence":0.7,"color":"danger","evidence":["当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","vacuum 阻碍时长与死元组/XID 年龄同步上涨是典型信号","定位 backend_xmin 非空的最老事务即元凶"]},
  {"cause":"失活复制槽或归档故障阻止 WAL 回收","confidence":0.6,"color":"danger","evidence":["复制槽滞留量持续增长说明下游停止消费","归档失败会同时出现在数据库日志中（archive command failed）"]},
  {"cause":"写入量超出 max_wal_size / autovacuum 吞吐配置","confidence":0.5,"color":"warning","evidence":["请求检查点频发 + 后端直刷偏高指向配置容量不足","属容量规划问题而非故障，可计划内调参"]}
]$J$::jsonb,
$J$[
  {"title":"查看维护类趋势","description":"确认 XID 消耗、死元组、滞留 WAL 的增长起点与斜率，判断是突发还是慢性累积","action":"查看实时概况","link":"pg_realtime"},
  {"title":"定位元凶事务/复制槽","description":"按 backend_xmin 找最老事务；按 pg_replication_slots 查滞留槽与失活槽","action":"查看知识库","link":"knowledge"},
  {"title":"安排低峰维护","description":"VACUUM FREEZE / pg_repack / 调参（max_wal_size 等）均建议低峰窗口执行","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"查看高龄库", "risk":"low","description":"确认 XID 年龄最高的库","sql":"SELECT datname, age(datfrozenxid) AS xid_age FROM pg_database ORDER BY 2 DESC;","impact":"只读查询，无风险"},
  {"action":"查看复制槽滞留量","risk":"low","description":"确认各复制槽的消费状态与滞留 WAL","sql":"SELECT slot_name, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained\nFROM pg_replication_slots ORDER BY 3 DESC;","impact":"只读查询，无风险"},
  {"action":"对高龄大表人工 FREEZE（人工确认）","risk":"medium","description":"低峰期对 XID 年龄最高的大表执行","sql":"-- 低峰执行，表级锁与 IO 压力须评估\nVACUUM (FREEZE, VERBOSE) <schema.table>;","impact":"运行期间产生大量 IO，建议低峰窗口执行"},
  {"action":"删除废弃复制槽（人工确认）","risk":"high","description":"确认下游已永久下线后执行","sql":"SELECT pg_drop_replication_slot('<slot_name>');","impact":"删除后下游无法续传，须先确认该槽确已废弃"}
]$J$::jsonb,
TRUE, TRUE, 24, 'PG 膨胀/XID 回卷/WAL 堆积/检查点类告警与对应场景'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_maintenance');

-- ---- 5. 知识库文章（配套新增规则与场景） ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES

  -- 5.1 XID 回卷（配套 builtin.pg.xid.wraparound / scenario.pg_wraparound_risk）
  ('PostgreSQL 事务 ID（XID）回卷风险处置', 'fault', '["PostgreSQL","XID回卷","VACUUM FREEZE","autovacuum","故障诊断"]',
   '<h2>什么是 XID 回卷</h2><p>PostgreSQL 用 32 位事务 ID 判断行版本可见性，约 40 亿个后循环使用。为避免新旧事务无法区分，每行必须在年龄达到约 20 亿之前被 freeze。若 freeze 长期跟不上，实例会先告警（WARNING: database must be vacuumed），最终<strong>拒绝所有写入</strong>，只能停机进入单用户模式修复——这是 PG 最严重的运维事故之一。</p><h2>排查步骤</h2><ol><li><strong>确认消耗程度</strong>：<code>SELECT datname, age(datfrozenxid) AS xid_age FROM pg_database ORDER BY 2 DESC;</code> 默认 autovacuum_freeze_max_age=2 亿（约 10%），监控值超过 30%~40% 说明回收长期失效。</li><li><strong>找高龄表</strong>：<code>SELECT relname, age(relfrozenxid) FROM pg_class WHERE relkind=''r'' ORDER BY 2 DESC LIMIT 20;</code></li><li><strong>找失效原因</strong>（三大元凶）：长事务钉住水位（查 pg_stat_activity 中 backend_xmin 非空的最老事务）；失活复制槽（查 pg_replication_slots）；遗留的 prepared 事务（查 pg_prepared_xacts）。</li></ol><h2>处置建议</h2><ul><li>先消除元凶（结束长事务/删废弃槽/提交或回滚 prepared 事务），否则 VACUUM 做了也白做。</li><li>对高龄大表低峰执行 <code>VACUUM (FREEZE, VERBOSE) 表名;</code>，从年龄最高的开始。</li><li>长期治理：确认 autovacuum 未被关闭，写入量大的实例适当调大 autovacuum_max_workers 与 vacuum_cost_limit。</li></ul>'),

  -- 5.2 WAL 堆积（配套 builtin.pg.wal.archive_failed / builtin.pg.repl.slot_retention / scenario.pg_wal_pileup）
  ('PostgreSQL WAL 堆积与复制槽治理', 'fault', '["PostgreSQL","WAL","复制槽","归档","磁盘打满","故障诊断"]',
   '<h2>WAL 为什么会堆积</h2><p>pg_wal 目录中的 WAL 段在满足三个条件后才能回收：已被检查点覆盖、已归档成功（如开启 archive_mode）、已被所有复制槽消费。任何一个环节卡住，WAL 都会持续堆积直至磁盘写满、实例宕机。<strong>失活复制槽是生产环境 WAL 撑爆磁盘的第一大原因</strong>。</p><h2>排查步骤</h2><ol><li><strong>看归档状态</strong>：<code>SELECT * FROM pg_stat_archiver;</code> failed_count 增长说明 archive_command 失败——检查归档目标存储（NFS/对象存储）可用性、空间与权限，日志中有具体报错。</li><li><strong>看复制槽滞留</strong>：<code>SELECT slot_name, slot_type, active, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained FROM pg_replication_slots ORDER BY 4 DESC;</code> active=f 且滞留量持续增长的槽，说明下游（从库/逻辑订阅/CDC 工具如 Debezium）已停止消费。</li><li><strong>看 pg_wal 占用</strong>：<code>SELECT pg_size_pretty(sum(size)) FROM pg_ls_waldir();</code> 对照磁盘余量评估紧迫程度。</li></ol><h2>处置建议</h2><ul><li>归档失败：修复归档目标后 PG 自动重试补归档，无需人工干预段文件。</li><li>下游可恢复：优先恢复下游消费（重启从库/订阅进程），滞留 WAL 会自然回收。</li><li>下游确认废弃：<code>SELECT pg_drop_replication_slot(''槽名'');</code>（高风险：删除后下游无法续传，须 DBA 确认）。</li><li>预防：PG13+ 设置 <code>max_slot_wal_keep_size</code> 给复制槽滞留量兜底上限，宁可断开下游也不拖垮主库。</li><li><strong>切勿手工删除 pg_wal 下的文件</strong>，会直接损坏实例。</li></ul>'),

  -- 5.3 检查点调优（配套 builtin.pg.ckpt.storm / scenario.pg_checkpoint_pressure）
  ('PostgreSQL 检查点与刷盘参数调优', 'performance', '["PostgreSQL","checkpoint","max_wal_size","bgwriter","性能优化"]',
   '<h2>两种检查点的区别</h2><p>定时检查点（checkpoints_timed，由 checkpoint_timeout 触发）是健康形态；请求检查点（checkpoints_req，WAL 写满 max_wal_size 被迫触发）频繁出现则是不健康信号：检查点间隔被压缩，每次检查点后的首次页面修改都要写全页镜像（full page write），写放大显著，表现为周期性 IO 尖刺与 TPS 锯齿。</p><h2>判断方法</h2><ol><li>监控趋势中「请求检查点次数」持续大于 0，且与 IO/延迟抖动周期吻合。</li><li>「后端直刷速率」（buffers_backend）占比高，说明 bgwriter 和检查点都来不及刷，后端进程被迫自己刷脏页，用户查询直接承担 IO 成本。</li></ol><h2>调优建议</h2><ul><li><code>max_wal_size</code>：从默认 1GB 起按写入量调大（写入密集实例常设 4~16GB），目标是让绝大多数检查点回归定时触发；代价是崩溃恢复时间变长与 pg_wal 占用增加。</li><li><code>checkpoint_completion_target</code>：保持 0.9（PG14+ 默认），把刷盘平摊到整个周期。</li><li>后端直刷高时：调大 <code>bgwriter_lru_maxpages</code>、缩短 <code>bgwriter_delay</code>，或评估增大 shared_buffers。</li><li>参数调整须在低峰窗口执行并观察一个完整业务周期，一次只改一组参数。</li></ul>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 6. 场景关联知识库文章 ----
UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 事务 ID（XID）回卷风险处置', 'PostgreSQL 长事务与表膨胀排查指南'))
 WHERE s.scenario_code = 'scenario.pg_wraparound_risk';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL WAL 堆积与复制槽治理'))
 WHERE s.scenario_code = 'scenario.pg_wal_pileup';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 检查点与刷盘参数调优'))
 WHERE s.scenario_code = 'scenario.pg_checkpoint_pressure';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 缓存命中率与临时文件优化'))
 WHERE s.scenario_code = 'scenario.pg_temp_spill';
