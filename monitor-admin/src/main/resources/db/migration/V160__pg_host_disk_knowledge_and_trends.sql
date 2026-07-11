-- PostgreSQL 主机磁盘告警：补齐专属知识库、真实采集频率与直达排查链接。

INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT
    'PostgreSQL 主机磁盘空间不足排查与处置',
    'fault',
    '["PostgreSQL","主机监控","磁盘空间","WAL","复制槽","故障诊断"]'::jsonb,
    $HTML$
<h2>告警含义与处理原则</h2>
<p>该告警来自 PostgreSQL 实例关联主机的最高磁盘使用率。磁盘写满可能导致 WAL 无法写入、事务提交失败、数据库异常停机。处置时先确认告警挂载点，再判断空间由 PGDATA、pg_wal、日志、归档、备份还是其他主机目录占用。</p>
<p><strong>安全红线：</strong>禁止直接删除 PGDATA 内的数据文件、<code>pg_wal</code> 文件或状态不明的复制槽。此类操作可能造成实例无法启动或下游复制永久中断。</p>

<h2>一、确认挂载点与空间来源</h2>
<ol>
  <li>主机执行 <code>df -hT</code>，确认使用率最高的挂载点及文件系统类型。</li>
  <li>数据库执行 <code>SHOW data_directory;</code>、<code>SHOW log_directory;</code>、<code>SHOW archive_command;</code>，确认数据、日志和归档实际目录。</li>
  <li>主机执行 <code>du -xhd1 &lt;挂载点&gt; | sort -h</code>，只在告警文件系统内逐级定位大目录，避免跨挂载点统计失真。</li>
  <li>数据库执行 <code>SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database ORDER BY pg_database_size(datname) DESC;</code>，判断业务库容量是否与主机占用同步增长。</li>
</ol>

<h2>二、PostgreSQL 专项排查</h2>
<ul>
  <li><strong>WAL 生成过快：</strong>对照平台 WAL 生成速率与写入高峰，排查批量导入、大事务、频繁全页写及检查点配置。</li>
  <li><strong>归档失败：</strong>查询 <code>pg_stat_archiver</code> 的 <code>failed_count</code>、<code>last_failed_time</code> 和 <code>last_failed_wal</code>；修复归档目标、权限或网络后确认成功计数继续增长。</li>
  <li><strong>复制槽滞留 WAL：</strong>查询 <code>SELECT slot_name, slot_type, active, wal_status, pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained FROM pg_replication_slots;</code>。只有确认消费者永久下线并完成影响评估后，才可由 DBA 删除槽位。</li>
  <li><strong>临时文件：</strong>查询 <code>pg_stat_database.temp_bytes</code>，结合 <code>log_temp_files</code> 定位产生大排序、哈希或物化的 SQL；优先优化 SQL，谨慎调整 work_mem。</li>
  <li><strong>表和索引膨胀：</strong>检查 <code>pg_stat_user_tables.n_dead_tup</code>、autovacuum 时间和 Top 大表。普通 VACUUM 不会把空间归还操作系统；需要归还时评估 pg_repack，VACUUM FULL 会锁表。</li>
</ul>

<h2>三、处置顺序</h2>
<ol>
  <li><strong>应急止血：</strong>按既定保留策略清理可再生成的应用日志、过期备份和已确认无用的归档副本，为数据库恢复安全余量。</li>
  <li><strong>修复根因：</strong>恢复 WAL 归档、修复复制消费者、优化临时文件大户 SQL或治理表膨胀。不要用手工删除 WAL 掩盖根因。</li>
  <li><strong>容量治理：</strong>按实例容量与磁盘使用率趋势估算耗尽时间；业务自然增长无法通过清理覆盖时，提前扩容磁盘并完成文件系统扩展验证。</li>
  <li><strong>处置后验证：</strong>确认磁盘可用空间、WAL 归档、复制状态、数据库写入和监控趋势均恢复正常，再关闭事件。</li>
</ol>
    $HTML$,
    'system', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM knowledge_article
    WHERE title = 'PostgreSQL 主机磁盘空间不足排查与处置'
);

UPDATE alert_drilldown_profile p
SET related_metrics = $J$[
      {"code":"host.disk.usage_max","label":"磁盘使用率（最高挂载点）","unit":"%","color":"#E5484D","frequency":"1m"},
      {"code":"host.diskio.util_max","label":"磁盘 IO 繁忙度","unit":"%","color":"#6366F1","frequency":"1m"},
      {"code":"pg.capacity.total_size_bytes","label":"实例总容量","unit":"GB","color":"#15A36A","toGB":true,"frequency":"1h"}
    ]$J$::jsonb,
    steps = jsonb_build_array(
      jsonb_build_object('title','确认告警挂载点与占用目录','description','查看 PG 关联主机的挂载点、磁盘使用率和 IO 趋势，确认 PGDATA、pg_wal、日志或备份所在文件系统','action','查看 PG 实时概况','link','pg_realtime'),
      jsonb_build_object('title','检查 WAL、归档与复制槽','description','核对 WAL 生成速率、归档失败和失效复制槽，确认是否存在 WAL 异常保留','action','前往 PG 复制监控','link','pg_replication'),
      jsonb_build_object('title','对照容量与性能趋势','description','结合实例总容量、临时文件和 Top SQL 判断持续增长或突发占用来源','action','前往 PG 性能分析','link','pg_performance'),
      jsonb_build_object('title','按专属手册制定处置方案','description','按 PostgreSQL 安全边界执行清理、根因治理或扩容，禁止直接删除 pg_wal','action','打开专属知识文章','link','/system/knowledge?articleId=' || k.id)
    ),
    updated_at = now()
FROM knowledge_article k
WHERE p.profile_code = 'pg_host_disk'
  AND p.db_type = 'postgresql'
  AND k.title = 'PostgreSQL 主机磁盘空间不足排查与处置';
