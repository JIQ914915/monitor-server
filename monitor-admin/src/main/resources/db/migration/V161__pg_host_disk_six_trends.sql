-- 恢复事件分析页 2x3 六图布局，并使用 PostgreSQL 磁盘诊断相关指标。
-- V160 已上线的环境通过本迁移增量更新；新环境也会在 V160 后得到相同结果。
UPDATE alert_drilldown_profile
SET related_metrics = $J$[
      {"code":"host.disk.usage_max","label":"磁盘使用率（最高挂载点）","unit":"%","color":"#E5484D","frequency":"1m"},
      {"code":"host.diskio.util_max","label":"磁盘 IO 繁忙度","unit":"%","color":"#6366F1","frequency":"1m"},
      {"code":"pg.capacity.total_size_bytes","label":"实例总容量","unit":"GB","color":"#15A36A","toGB":true,"frequency":"1h"},
      {"code":"pg.wal.write_rate","label":"WAL 生成速率","unit":"B/s","color":"#E08600","frequency":"1m"},
      {"code":"pg.rate.temp_bytes","label":"临时文件写入速率","unit":"B/s","color":"#0C7C97","frequency":"1m"},
      {"code":"pg.repl.slot_retained_bytes_max","label":"复制槽滞留 WAL 最大值","unit":"B","color":"#9B59B6","frequency":"1m"}
    ]$J$::jsonb,
    updated_at = now()
WHERE profile_code = 'pg_host_disk'
  AND db_type = 'postgresql';
