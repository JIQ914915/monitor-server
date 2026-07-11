-- PostgreSQL 主机磁盘告警必须使用 PG 专属画像，不能复用 MySQL 的 host_resource。
INSERT INTO alert_drilldown_profile
    (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions,
     builtin, enabled, sort, remark)
SELECT 'pg_host_disk', 'PostgreSQL 主机磁盘类', 'postgresql',
$J$[{"matchType":"exact","pattern":"host.disk.usage_max"}]$J$::jsonb,
$J$[
  {"code":"host.disk.usage_max","label":"磁盘使用率（最高挂载点）","unit":"%","color":"#E5484D"},
  {"code":"host.diskio.util_max","label":"磁盘 IO 繁忙度","unit":"%","color":"#6366F1"},
  {"code":"pg.wal.write_rate","label":"WAL 生成速率","unit":"bytes","color":"#E08600"},
  {"code":"pg.rate.temp_bytes","label":"临时文件写入速率","unit":"bytes","color":"#0C7C97"},
  {"code":"pg.capacity.total_size_bytes","label":"实例总容量","unit":"bytes","color":"#15A36A"}
]$J$::jsonb,
$J$[
  {"cause":"PostgreSQL 数据文件或 WAL 持续增长","confidence":0.8,"color":"danger","evidence":["当前磁盘使用率 {value}{unit}，已达到阈值 {threshold}{unit}","对照 WAL 生成速率与实例容量趋势，确认是业务数据增长、WAL 堆积还是突发写入"]},
  {"cause":"归档失败或失效复制槽导致 WAL 堆积","confidence":0.7,"color":"warning","evidence":["检查 pg_stat_archiver 的失败次数与最近失败时间","检查 pg_replication_slots 中 inactive 槽位及 retained WAL 大小，避免槽位长期保留 WAL"]},
  {"cause":"临时文件、数据库日志或主机其他文件占满磁盘","confidence":0.55,"color":"info","evidence":["临时文件写入速率升高通常来自大排序、哈希或 work_mem 不足","若 PG 容量与 WAL 趋势平稳，应按目录定位日志、备份或其他主机文件"]}
]$J$::jsonb,
$J$[
  {"title":"确认告警挂载点与占用目录","description":"登录主机查看最高使用率挂载点，确认 PGDATA、pg_wal、日志或备份目录是否位于该盘","action":"查看实时概况","link":"realtime"},
  {"title":"检查 WAL、归档与复制槽","description":"核对归档失败、无效复制槽和下游复制状态，确认是否存在 WAL 异常保留","action":"前往复制监控","link":"replication"},
  {"title":"对照写入与临时文件趋势","description":"结合 WAL 生成速率、临时文件写入速率和 Top SQL 判断突发增长来源","action":"前往性能分析","link":"performance"},
  {"title":"制定清理或扩容方案","description":"先释放可安全清理的日志/备份空间；业务数据自然增长时按增速评估扩容","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"查看数据库目录和 WAL 占用","risk":"low","description":"只读确认 PGDATA、pg_wal 与数据库容量，禁止直接删除 pg_wal 文件","sql":"-- 数据库内只读查询\nSELECT pg_size_pretty(sum(pg_database_size(datname))) AS databases_size FROM pg_database;\nSELECT pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), '0/0')) AS current_wal_lsn;\n-- 主机侧只读命令\ndf -h\ndu -xh <PGDATA>/* | sort -h | tail","impact":"只读检查，无风险"},
  {"action":"排查失效复制槽","risk":"medium","description":"先确认槽位对应消费者是否永久下线，再由 DBA 决定是否删除槽位","sql":"SELECT slot_name, slot_type, active, restart_lsn, wal_status\nFROM pg_replication_slots ORDER BY active, slot_name;\n-- 删除槽位属于高风险操作，须确认消费者永久停用后人工执行","impact":"误删仍在使用的复制槽会导致下游无法续传，必须人工确认"},
  {"action":"清理日志或扩容磁盘","risk":"medium","description":"优先按保留策略清理归档日志和备份；不得手工删除 PGDATA/pg_wal 内文件","sql":"-- 文件清理与磁盘扩容属于主机变更，请按组织变更流程人工执行","impact":"清理范围错误可能导致审计记录丢失；扩容需确认文件系统扩展步骤"}
]$J$::jsonb,
TRUE, TRUE, 7, 'PostgreSQL 实例关联主机的磁盘空间不足告警'
WHERE NOT EXISTS (
    SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_host_disk'
);
