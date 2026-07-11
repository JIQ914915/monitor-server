-- =============================================================
-- V147：主机指标监控（四）——场景 / 下钻画像 / 知识库配套
--   前置：V133 主机模型、V134 host.* 指标定义、V135 host 内置规则（11 条已覆盖
--   单指标阈值告警），本脚本补齐多信号联动与事件分析链路：
--   1. 知识库：主机层排查文章 6 篇（CPU / 内存 OOM / 磁盘空间 / IO 瓶颈 /
--      主机不可达与 exporter 部署 / 文件系统只读）
--   2. 内置场景 4 个（V117 当时因 OS 指标未采集而暂缓的「磁盘空间预警」在此落地）：
--        scenario.host_disk_exhaustion    磁盘空间预警（使用率高 + 仍在增长）
--        scenario.host_resource_saturation 主机算力饱和拖累数据库（CPU 高 + 延迟升）
--        scenario.host_io_bottleneck      主机 IO 瓶颈（IO 繁忙 + 延迟升）
--        scenario.host_memory_pressure    主机内存压力（内存高 + Swap 换页，Linux 口径）
--      场景求值按实例读 metric_data（主机指标已按关联实例扇出），Windows 主机缺失的
--      信号（swap/iowait）进入 UNKNOWN 分支不会误报。
--   3. 下钻画像 2 个 + 场景编码匹配规则，host.* 告警与主机场景事件的下钻页
--      不再落「通用类」兜底：
--        host_availability 主机可达类（exact host.availability / host.uptime）
--        host_resource     主机资源类（prefix host. + scenario.host_）
--   4. 场景关联知识库文章（按标题回查 id）
-- 注：全脚本幂等（按标题/编码防重，jsonb @> 存在性守卫）。
-- =============================================================

-- ---- 1. 知识库文章 ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES

  -- 1.1 主机 CPU（配套 builtin.host.cpu.high / scenario.host_resource_saturation）
  ('主机 CPU 飙高对数据库的影响与排查', 'fault', '["主机监控","CPU","故障诊断"]',
   '<h2>影响机制</h2><p>数据库进程与主机上其他进程共享 CPU。CPU 长时间接近打满时，SQL 执行、连接握手、后台刷盘线程都会排队，表现为语句平均延迟整体抬升、慢 SQL 数量增多，严重时连接超时。</p><h2>排查步骤</h2><ol><li><strong>区分消耗来源</strong>：在主机上执行 <code>top -c</code>（Windows 用任务管理器/资源监视器），确认 CPU 大户是 mysqld 还是其他进程（备份、杀毒扫描、应用混部）。</li><li><strong>mysqld 占用高</strong>：结合平台的 Top SQL 与活跃线程排查——大量全表扫描、无索引 JOIN、批量计算类 SQL 是常见原因；活跃线程数（Threads_running）远超 CPU 核数说明并发已过载。</li><li><strong>其他进程占用高</strong>：与主机管理员确认任务归属，评估迁移错峰或资源隔离；数据库主机不建议与高耗 CPU 应用混部。</li><li><strong>确认是否伴随 IOWait</strong>：CPU 使用率不高但负载（load）高、IOWait 高，瓶颈在磁盘而非算力，转向 IO 排查。</li></ol><h2>处置建议</h2><ul><li>短期：优化或限流 Top 耗 CPU 的 SQL，错峰批处理任务。</li><li>长期：单核负载持续大于 1～2 时评估扩容 CPU 或拆分实例。</li></ul>'),

  -- 1.2 主机内存与 OOM（配套 builtin.host.mem.high / builtin.host.swap.used / scenario.host_memory_pressure）
  ('主机内存不足与 OOM 风险排查', 'fault', '["主机监控","内存","OOM","故障诊断"]',
   '<h2>为什么数据库最怕主机内存不足</h2><p>Linux 内存耗尽时内核 OOM Killer 会优先终止占用内存最大的进程——数据库主机上通常就是 mysqld，表现为实例突然重启、错误日志无异常但进程消失。Swap 开始换页后即使未触发 OOM，性能也会剧烈抖动。</p><h2>排查步骤</h2><ol><li><strong>确认内存去向</strong>：<code>free -h</code> 与 <code>ps aux --sort=-rss | head</code> 查看占用排名（Windows 用任务管理器按内存排序）。</li><li><strong>核对 MySQL 内存配置</strong>：<code>innodb_buffer_pool_size</code> + 连接级缓冲（<code>sort_buffer_size</code>、<code>join_buffer_size</code> 等 × 最大连接数）之和应低于物理内存的 80%，为操作系统与其他进程留余量。</li><li><strong>检查是否已发生换页</strong>：Swap 使用率上升且持续，说明内存已实质不足；结合平台主机资源页的 Swap 趋势确认起始时间点。</li><li><strong>确认历史 OOM 记录</strong>：<code>dmesg -T | grep -i "out of memory"</code> 或 <code>/var/log/messages</code>，出现 mysqld 被 kill 的记录即为 OOM 实锤。</li></ol><h2>处置建议</h2><ul><li>紧急：清退主机上非必要的内存大户进程；确认无泄漏后再考虑临时调小 buffer pool（需重启或在线收缩，谨慎评估）。</li><li>长期：按数据热集大小规划内存容量；数据库主机建议独占部署，禁止与内存密集型应用混部。</li></ul>'),

  -- 1.3 磁盘空间（配套 builtin.host.disk.high/critical、builtin.host.inode.high / scenario.host_disk_exhaustion）
  ('主机磁盘空间不足的清理与扩容指南', 'fault', '["主机监控","磁盘","容量","故障诊断"]',
   '<h2>风险说明</h2><p>数据目录所在磁盘写满后，MySQL 无法写入数据文件、Binlog 与临时文件，会话会被挂起甚至实例崩溃；恢复往往需要先腾出空间才能重启，属于必须提前处置的告警。</p><h2>定位占用</h2><ol><li>在平台实例详情的主机资源页查看挂载点明细，确认接近写满的是数据盘、日志盘还是系统盘。</li><li>Linux 用 <code>du -sh /*</code> 逐层下钻（Windows 用磁盘分析工具），常见空间大户：Binlog、慢查询日志/通用日志、备份文件、core dump、未清理的临时文件。</li><li>数据库内确认对象占用：<code>information_schema.TABLES</code> 按 <code>data_length + index_length</code> 排序找 Top 大表。</li></ol><h2>清理顺序（从低风险到高风险）</h2><ol><li>过期备份文件、应用日志、core dump——与归属方确认后删除。</li><li>过期 Binlog：确认从库已同步后 <code>PURGE BINARY LOGS BEFORE ...</code>，并核对过期策略（8.0 <code>binlog_expire_logs_seconds</code>）。</li><li>历史数据归档 + 碎片回收（参见《表空间容量增长分析与归档策略》）。</li></ol><h2>Inode 耗尽的特殊情况</h2><p>磁盘显示有剩余空间但无法创建文件时，用 <code>df -i</code> 检查 inode 使用率；海量小文件（会话文件、缓存文件）是典型诱因，需按目录统计文件数定位后批量清理。</p>'),

  -- 1.4 磁盘 IO（配套 builtin.host.diskio.busy / builtin.host.iowait.high / scenario.host_io_bottleneck）
  ('磁盘 IO 瓶颈的识别与排查', 'performance', '["主机监控","磁盘IO","性能优化"]',
   '<h2>识别信号</h2><ul><li>磁盘 IO 繁忙度（util）持续 ≥ 90%：设备已接近饱和，新请求开始排队。</li><li>CPU IOWait 占比高（Linux）：CPU 空转等待 IO 完成，算力被浪费。</li><li>数据库侧同步表现：语句延迟上升、刷脏页变慢、复制回放延迟增大。</li></ul><h2>排查步骤</h2><ol><li><strong>确认繁忙的是哪块盘</strong>：平台主机资源页的磁盘 IO 趋势按盘展示繁忙度与读写速率，先确认是数据盘还是其他盘。</li><li><strong>区分读压力与写压力</strong>：读速率高——Buffer Pool 命中率不足或大查询全表扫描；写速率高——批量写入、大事务、脏页集中刷盘或备份任务。</li><li><strong>定位数据库内源头</strong>：读压力结合 Top SQL 找扫描行数大的语句；写压力检查批处理任务时间窗与 binlog 写入量。</li><li><strong>排除数据库外因素</strong>：备份、日志采集、杀毒扫描等主机任务与数据库争抢同一块盘是常见诱因。</li></ol><h2>处置建议</h2><ul><li>短期：错峰备份/批处理，优化高扫描 SQL，必要时增大 Buffer Pool 减少物理读。</li><li>长期：数据、日志、备份分盘部署；机械盘升级 SSD 对随机 IO 改善显著。</li></ul>'),

  -- 1.5 主机不可达与 exporter 部署（配套 builtin.host.unreachable）
  ('主机不可达告警排查与 exporter 部署指南', 'practice', '["主机监控","exporter","采集","运维"]',
   '<h2>告警含义</h2><p>平台通过主机上部署的 exporter（Linux 为 node_exporter，Windows 为 windows_exporter）拉取指标，连续多个周期拉取失败即触发「主机不可达」。注意：exporter 不可达不等于数据库宕机，需按顺序确认。</p><h2>排查顺序</h2><ol><li><strong>先确认数据库实例状态</strong>：平台实例可用性正常而仅主机指标中断，说明是 exporter 或其端口的问题，风险等级可降。</li><li><strong>确认主机存活</strong>：ping / SSH（或远程桌面）能否登录；主机宕机时数据库告警会同时出现。</li><li><strong>确认 exporter 进程</strong>：Linux <code>systemctl status node_exporter</code>；Windows 服务列表检查 windows_exporter 服务状态。</li><li><strong>确认网络与防火墙</strong>：在监控服务器上 <code>curl http://主机IP:9100/metrics</code>（windows_exporter 默认 9182）验证连通性，检查防火墙与安全组是否放行。</li><li><strong>使用平台测试功能</strong>：主机管理页的「测试连接」会校验连通性与 exporter 类型是否与配置的操作系统匹配。</li></ol><h2>部署要点</h2><ul><li>Linux：node_exporter 以 systemd 服务运行并设置开机自启，默认端口 9100。</li><li>Windows：windows_exporter 以 Windows 服务安装，默认端口 9182，建议启用 cpu/memory/logical_disk/physical_disk/net/os 采集器。</li><li>仅对监控服务器地址放行 exporter 端口，避免指标端点暴露到不可信网络。</li></ul>'),

  -- 1.6 文件系统只读（配套 builtin.host.fs.readonly）
  ('文件系统只读故障的应急处理', 'fault', '["主机监控","磁盘","文件系统","故障诊断"]',
   '<h2>故障特征</h2><p>Linux 检测到磁盘或文件系统错误时会将挂载点重挂为只读（read-only）以保护数据。数据库表现为写入立即报错（如 <code>Error 30: Read-only file system</code>）、事务无法提交，属最高优先级故障。</p><h2>应急处理顺序</h2><ol><li><strong>确认影响范围</strong>：<code>mount | grep ro</code> 找出只读挂载点，确认是否覆盖数据目录、Binlog 目录。</li><li><strong>查看内核日志定因</strong>：<code>dmesg -T | grep -iE "error|ext4|xfs|I/O"</code>，常见原因：磁盘坏道、RAID 降级、存储链路（SAN/云盘）抖动、文件系统元数据损坏。</li><li><strong>不要急于重挂读写</strong>：底层磁盘故障未排除前强行 <code>mount -o remount,rw</code> 可能加剧数据损坏；先联系主机/存储管理员确认硬件状态。</li><li><strong>评估切换</strong>：主从架构下优先切换到从库恢复业务，再处理故障主机。</li><li><strong>修复后校验</strong>：文件系统修复（fsck/xfs_repair）后启动 MySQL，确认 InnoDB 崩溃恢复完成、主从数据一致后再回切。</li></ol><h2>预防措施</h2><ul><li>关注磁盘 SMART 告警与 RAID 状态巡检，提前更换风险盘。</li><li>保持备份可用并定期演练恢复，只读故障可能伴随数据损坏。</li></ul>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 2. 内置场景 ----

-- [1] 磁盘空间预警（V117 暂缓项落地）：使用率已高 + 仍在增长 → 提前于写满告警介入
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.host_disk_exhaustion',
    '磁盘空间预警',
    '磁盘使用率偏高且较一天前仍在增长时提前预警，区分「稳定高位」和「持续增长即将写满」两种形态，'
    '早于磁盘空间不足规则（85%/95%）介入，为清理归档争取时间窗',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":0,"children":[
       {"type":"condition","code":"disk_high","name":"磁盘使用率（最高挂载点）","metricCode":"host.disk.usage_max",
        "condType":"threshold","operator":">=","threshold":75,"unit":"%","exprText":"≥ 75%"},
       {"type":"condition","code":"disk_growing","name":"磁盘使用率环比","metricCode":"host.disk.usage_max",
        "condType":"rate_change","compareOffset":"1d","operator":">=","threshold":3,"unit":"%",
        "exprText":"较1天前上涨 ≥ 3%（仍在增长）"}
     ]}'::jsonb,
    '{"duration":600}'::jsonb,
    '磁盘使用率已偏高且仍在持续增长，按当前增速可能在数日内写满；'
    '建议尽快清理 Binlog/日志/备份文件并评估归档扩容，挂载点明细见实例详情主机资源页',
    NULL,
    5, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [2] 主机算力饱和拖累数据库（AND）：CPU 高 + 数据库延迟同步上升 → 排除"CPU 高但业务无感"的误报
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.host_resource_saturation',
    '主机算力饱和拖累数据库',
    '主机 CPU 持续高位且数据库语句延迟同步上升时触发，确认算力瓶颈已实际影响业务，'
    '区分「数据库自身高耗 CPU 查询」与「主机其他进程抢占资源」两种根因',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":180,"children":[
       {"type":"condition","code":"cpu_high","name":"主机 CPU 使用率","metricCode":"host.cpu.usage",
        "condType":"threshold","operator":">=","threshold":85,"unit":"%","exprText":"≥ 85%"},
       {"type":"condition","code":"latency_high","name":"语句平均延迟","metricCode":"mysql.perf.avg_stmt_latency_ms",
        "condType":"threshold","operator":">=","threshold":500,"unit":"ms","exprText":"≥ 500ms"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '主机 CPU 接近饱和且数据库语句延迟明显上升，算力瓶颈已影响业务；'
    '请在主机上确认 CPU 大户是 mysqld 还是其他进程：前者结合 Top SQL 优化高耗查询，后者协调错峰或迁移',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [3] 主机 IO 瓶颈（AND）：磁盘接近饱和 + 数据库延迟同步上升（信号在 Linux/Windows 均可采集）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.host_io_bottleneck',
    '主机 IO 瓶颈拖累数据库',
    '磁盘 IO 繁忙度接近饱和且数据库语句延迟同步上升时触发，确认 IO 瓶颈已实际影响业务；'
    '需区分读压力（缓存不足/全表扫描）与写压力（批量写入/刷脏/备份任务）',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":180,"children":[
       {"type":"condition","code":"io_busy","name":"磁盘 IO 繁忙度（最高设备）","metricCode":"host.diskio.util_max",
        "condType":"threshold","operator":">=","threshold":85,"unit":"%","exprText":"≥ 85%"},
       {"type":"condition","code":"latency_high","name":"语句平均延迟","metricCode":"mysql.perf.avg_stmt_latency_ms",
        "condType":"threshold","operator":">=","threshold":300,"unit":"ms","exprText":"≥ 300ms"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '磁盘 IO 繁忙度接近饱和且数据库延迟上升，IO 已成为瓶颈；'
    '请在主机资源页按盘查看读写速率区分读/写压力：读压力结合 Top SQL 排查全表扫描与缓存命中，'
    '写压力检查批量写入、脏页刷盘与备份任务时间窗',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [4] 主机内存压力（AND，Linux 口径）：内存高位 + Swap 已实质换页 → OOM 风险
--     Windows 主机不产出 host.swap.usage，该信号进入 UNKNOWN 分支不会误报（内存单阈值已由内置规则覆盖）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.host_memory_pressure',
    '主机内存压力（OOM 风险）',
    '内存使用率高位且 Swap 已开始实质换页时触发（Linux 口径），比单一内存阈值更接近真实 OOM 风险：'
    '换页发生说明可用内存已耗尽，mysqld 存在被 OOM Killer 终止的风险',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":180,"children":[
       {"type":"condition","code":"mem_high","name":"主机内存使用率","metricCode":"host.mem.usage",
        "condType":"threshold","operator":">=","threshold":85,"unit":"%","exprText":"≥ 85%"},
       {"type":"condition","code":"swap_active","name":"Swap 使用率","metricCode":"host.swap.usage",
        "condType":"threshold","operator":">=","threshold":10,"unit":"%","exprText":"≥ 10%（已实质换页）"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '主机内存高位且 Swap 已开始换页，mysqld 存在被 OOM 终止的风险；'
    '请立即确认主机内存大户进程，核对 innodb_buffer_pool_size 与连接级缓冲配置之和是否超出物理内存承载',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 3. 下钻画像 ----

-- 3.1 主机可达类（exact 优先命中，避免被 host. 前缀吞掉）
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'host_availability', '主机可达类', 'mysql',
$J$[
  {"matchType":"exact","pattern":"host.availability"},
  {"matchType":"exact","pattern":"host.uptime"}
]$J$::jsonb,
$J$[
  {"code":"host.availability","label":"主机可达性（0/1）","unit":"","color":"#E5484D"},
  {"code":"mysql.qps","label":"QPS","unit":"","color":"#15A36A"},
  {"code":"mysql.conn.total","label":"当前连接数","unit":"","color":"#0C7C97"},
  {"code":"host.cpu.usage","label":"主机 CPU 使用率","unit":"%","color":"#E08600"}
]$J$::jsonb,
$J$[
  {"cause":"exporter 进程退出或端口不通","confidence":0.7,"color":"warning","evidence":["数据库实例指标（QPS/连接数）如仍正常更新，说明主机存活、仅 exporter 采集链路中断","检查 node_exporter（9100）/ windows_exporter（9182）服务状态与防火墙放行"]},
  {"cause":"主机宕机或网络中断","confidence":0.6,"color":"danger","evidence":["数据库指标如同时中断（趋势断档），主机整体不可达的可能性高","ping/SSH 无响应时按主机故障应急处理，主从架构评估切换"]},
  {"cause":"主机变更或重启窗口","confidence":0.4,"color":"info","evidence":["核对变更记录：主机重启、迁移、安全加固都可能临时中断采集","host.uptime 在恢复后回落到较小值可确认发生过重启"]}
]$J$::jsonb,
$J$[
  {"title":"确认数据库实例是否存活","description":"先看实例可用性与 QPS/连接数是否正常更新，区分「主机宕机」与「仅采集中断」两种严重度","action":"查看实时概况","link":"realtime"},
  {"title":"检查 exporter 服务与网络","description":"登录主机确认 exporter 进程与端口，或用主机管理页「测试连接」验证连通性","action":"查看知识库","link":"knowledge"},
  {"title":"查看采集任务日志","description":"确认采集失败的具体错误信息（连接拒绝/超时/类型不匹配）","action":"查看采集任务","link":"collector"}
]$J$::jsonb,
$J$[
  {"action":"验证 exporter 端点连通性","risk":"low","description":"在监控服务器上手动拉取指标端点验证","sql":"-- Linux 主机（node_exporter）\ncurl -s http://<主机IP>:9100/metrics | head\n-- Windows 主机（windows_exporter）\ncurl -s http://<主机IP>:9182/metrics | head","impact":"只读验证，无风险"},
  {"action":"重启 exporter 服务","risk":"low","description":"确认进程退出后重启采集服务","sql":"-- Linux\nsystemctl restart node_exporter && systemctl status node_exporter\n-- Windows（管理员 PowerShell）\nRestart-Service windows_exporter","impact":"仅影响指标采集，不影响数据库运行"}
]$J$::jsonb,
TRUE, TRUE, 8, '主机不可达/主机运行时长类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'host_availability');

-- 3.2 主机资源类（prefix host. 覆盖 CPU/内存/磁盘/IO/网络全部 host.* 指标 + 主机场景事件）
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'host_resource', '主机资源类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"host."},
  {"matchType":"prefix","pattern":"scenario.host_"}
]$J$::jsonb,
$J$[
  {"code":"host.cpu.usage","label":"主机 CPU 使用率","unit":"%","color":"#E5484D"},
  {"code":"host.mem.usage","label":"主机内存使用率","unit":"%","color":"#E08600"},
  {"code":"host.disk.usage_max","label":"磁盘使用率（最高挂载点）","unit":"%","color":"#9B59B6"},
  {"code":"host.diskio.util_max","label":"磁盘 IO 繁忙度","unit":"%","color":"#6366F1"},
  {"code":"mysql.perf.avg_stmt_latency_ms","label":"语句平均延迟","unit":"ms","color":"#0C7C97"},
  {"code":"mysql.delta.slow_queries","label":"慢SQL数/分钟","unit":"","color":"#15A36A"}
]$J$::jsonb,
$J$[
  {"cause":"数据库自身负载过高消耗主机资源","confidence":0.75,"color":"danger","evidence":["当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","慢SQL数与语句延迟如同步上升，资源消耗大概率来自数据库内部（全表扫描、批量写入、大事务）","结合 Top SQL 确认告警时段的高耗语句"]},
  {"cause":"主机上其他进程抢占资源","confidence":0.6,"color":"warning","evidence":["数据库负载（QPS/延迟）平稳但主机资源打满，指向备份、杀毒扫描、混部应用等数据库外进程","需登录主机按进程排序确认资源大户归属"]},
  {"cause":"容量规划不足（业务自然增长）","confidence":0.45,"color":"info","evidence":["资源使用率随业务量长期缓慢爬升、无突发拐点，属容量不足","按趋势图预估耗尽时间，纳入扩容计划"]}
]$J$::jsonb,
$J$[
  {"title":"查看主机资源全景","description":"在实例详情的主机资源页查看 CPU/内存/磁盘/IO/网络趋势与挂载点明细，确认瓶颈资源与起始时间","action":"查看实时概况","link":"realtime"},
  {"title":"确认数据库内部源头","description":"对照告警时段的 Top SQL 与慢 SQL，确认是否由高耗语句引起","action":"前往慢SQL分析","link":"slowsql"},
  {"title":"对比数据库负载趋势","description":"QPS/延迟与主机资源是否同步异动，区分数据库内因与主机外因","action":"前往性能分析","link":"performance"},
  {"title":"查阅主机排查手册","description":"按资源类型查看对应的排查文章（CPU 飙高 / 内存 OOM / 磁盘空间 / IO 瓶颈）","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"定位主机资源大户进程","risk":"low","description":"登录主机确认资源消耗归属，区分 mysqld 与其他进程","sql":"-- Linux：CPU/内存排序\ntop -c -o %CPU\nps aux --sort=-rss | head -10\n-- 磁盘 IO 按进程（需 sysstat/iotop）\niotop -oPa\n-- Windows：资源监视器（resmon）按 CPU/内存/磁盘排序","impact":"只读查看，无风险"},
  {"action":"优化告警时段的高耗 SQL","risk":"low","description":"对 Top 耗资源语句检查执行计划并优化索引","sql":"-- 按总耗时定位 Top 语句\nSELECT DIGEST_TEXT, COUNT_STAR, SUM_TIMER_WAIT/1e12 AS total_s, SUM_ROWS_EXAMINED\nFROM performance_schema.events_statements_summary_by_digest\nORDER BY SUM_TIMER_WAIT DESC LIMIT 10;","impact":"索引变更建议低峰期执行"},
  {"action":"清理磁盘空间（磁盘类告警）","risk":"medium","description":"确认从库已同步后清理过期 Binlog，联动清理备份与日志文件","sql":"-- 查看 Binlog 占用\nSHOW BINARY LOGS;\n-- 确认从库位点后按日期清理\nPURGE BINARY LOGS BEFORE DATE_SUB(NOW(), INTERVAL 3 DAY);","impact":"清理前必须确认从库已同步到对应位点，否则复制中断"},
  {"action":"资源扩容评估","risk":"medium","description":"按趋势增速评估 CPU/内存/磁盘扩容规格，走线下变更流程","sql":"-- 扩容属基础设施变更，请按增长趋势预估规格后走线下审批流程执行","impact":"需要变更窗口；虚拟化环境部分资源支持在线扩容"}
]$J$::jsonb,
TRUE, TRUE, 9, '主机 CPU/内存/磁盘/IO/网络类告警与主机场景事件'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'host_resource');

-- ---- 4. 场景关联知识库文章（按标题回查 id） ----
UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('主机磁盘空间不足的清理与扩容指南', '表空间容量增长分析与归档策略'))
 WHERE s.scenario_code = 'scenario.host_disk_exhaustion';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('主机 CPU 飙高对数据库的影响与排查', 'Top SQL 分析方法'))
 WHERE s.scenario_code = 'scenario.host_resource_saturation';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('磁盘 IO 瓶颈的识别与排查', '文件系统只读故障的应急处理'))
 WHERE s.scenario_code = 'scenario.host_io_bottleneck';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('主机内存不足与 OOM 风险排查', 'MySQL 实例意外重启排查指南'))
 WHERE s.scenario_code = 'scenario.host_memory_pressure';
