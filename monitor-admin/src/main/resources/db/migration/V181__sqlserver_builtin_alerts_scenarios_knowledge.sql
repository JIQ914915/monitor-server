-- SQL Server 内置运维内容补全：告警规则、综合场景、知识库与事件下钻画像。
-- 所有规则只引用 V172~V176 已落地的采集指标；建议动作以只读核查为主，
-- 不执行自动终止会话、自动故障转移、自动改参数或自动建删索引。

-- ---- 1. 内置告警规则：补齐现有指标中尚未覆盖的关键风险 ----
INSERT INTO alert_rule
(rule_name, rule_code, rule_level, db_type_id, db_version_ids, metric_name,
 condition_config, recovery_config, notification_config, scan_interval_min,
 scan_interval_source, created_by, description, recommended)
VALUES
('SQL Server 长请求持续运行','builtin.sqlserver.request.long','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.request.max_seconds',
 '{"operator":">=","threshold":300,"duration":180,"unit":"second"}',
 '{"operator":"<","threshold":180}',NULL,1,'SYSTEM_DEFAULT','system',
 '最长活跃请求持续超过 5 分钟；请结合阻塞链、等待类型和 Top SQL 人工判断，平台不自动终止会话',TRUE),
('SQL Server 日志复用持续受阻','builtin.sqlserver.log_reuse_blocked','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.storage.log_reuse_blocked',
 '{"operator":">","threshold":0,"duration":300,"unit":"count"}',
 '{"operator":"<=","threshold":0}',NULL,1,'SYSTEM_DEFAULT','system',
 'log_reuse_wait_desc 持续非 NOTHING；请核对日志备份、长事务、AG/复制状态和磁盘空间',TRUE),
('SQL Server 文件读取延迟','builtin.sqlserver.io.read_latency','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.io.read_latency_ms',
 '{"operator":">=","threshold":20,"duration":300,"unit":"ms"}',
 '{"operator":"<","threshold":15}',NULL,1,'SYSTEM_DEFAULT','system',
 '数据库文件平均读取延迟持续偏高；请关联 PAGEIOLATCH、Top SQL 物理读和主机磁盘指标',TRUE),
('SQL Server 日志备份逾期','builtin.sqlserver.backup.log_overdue','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.backup.max_log_age_minutes',
 '{"operator":">","threshold":60,"duration":0,"unit":"minute"}',
 '{"operator":"<=","threshold":30}',NULL,5,'SYSTEM_DEFAULT','system',
 'FULL/BULK_LOGGED 用户库日志备份超过 60 分钟；请核对 RPO、备份作业及日志复用等待',TRUE),
('SQL Server 数据库缺少日志备份','builtin.sqlserver.backup.log_missing','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.backup.log_missing_database_count',
 '{"operator":">","threshold":0,"duration":0,"unit":"count"}',
 '{"operator":"<=","threshold":0}',NULL,5,'SYSTEM_DEFAULT','system',
 '存在 FULL/BULK_LOGGED 用户库没有日志备份记录；请核对备份策略与恢复目标',TRUE),
('SQL Server AG 数据库同步异常','builtin.sqlserver.ag.unhealthy','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.ag.unhealthy_databases',
 '{"operator":">","threshold":0,"duration":180,"unit":"count"}',
 '{"operator":"<=","threshold":0}',NULL,1,'SYSTEM_DEFAULT','system',
 'Always On 数据库同步健康状态持续异常；请核对副本连接、发送/重做队列和错误日志，不自动故障转移',TRUE),
('SQL Server AG 数据移动暂停','builtin.sqlserver.ag.suspended','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.ag.suspended_databases',
 '{"operator":">","threshold":0,"duration":180,"unit":"count"}',
 '{"operator":"<=","threshold":0}',NULL,1,'SYSTEM_DEFAULT','system',
 'Always On 数据库数据移动持续暂停；恢复数据移动前需由 DBA 确认暂停原因和数据状态',TRUE),
('SQL Server AG 重做积压','builtin.sqlserver.ag.redo_lag','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.ag.max_redo_seconds',
 '{"operator":">","threshold":300,"duration":300,"unit":"second"}',
 '{"operator":"<=","threshold":120}',NULL,1,'SYSTEM_DEFAULT','system',
 '辅助副本按当前速率估算重做超过 5 分钟；请检查辅助副本 I/O、CPU、长查询和日志生成速率',TRUE),
('SQL Server 日志传送备份延迟','builtin.sqlserver.log_shipping.backup_delay','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,'sqlserver.log_shipping.backup_delay_minutes',
 '{"operator":">","threshold":30,"duration":300,"unit":"minute"}',
 '{"operator":"<=","threshold":15}',NULL,5,'SYSTEM_DEFAULT','system',
 '日志传送主库备份阶段持续延迟；请核对备份作业、共享路径和事务日志状态',TRUE)
ON CONFLICT (rule_code) DO NOTHING;

-- ---- 2. SQL Server 专属知识库文章 ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES
('SQL Server CPU 与 Worker 排队排查指南','sqlserver','["SQL Server","CPU","Worker","等待统计","性能优化"]',
 '<h2>判定思路</h2><p>runnable_tasks 持续升高表示任务在等待 CPU 调度，但不能只凭单点值下结论。应同时核对主机 CPU、SOS_SCHEDULER_YIELD 类等待、活跃请求和 Top SQL。</p><h2>排查步骤</h2><ol><li>在性能分析查看 CPU 等待与 runnable_tasks 的同一时段趋势。</li><li>在 Top SQL 按 CPU 时间定位主要消耗语句，并核对执行次数是否突增。</li><li>核对并行度、近期发布和统计信息变化。</li></ol><h2>建议</h2><p>优先优化高 CPU SQL 和不必要扫描。参数调整、资源扩容或终止会话必须由 DBA 评估后执行。</p>'),
('SQL Server 查询内存授权等待排查','sqlserver','["SQL Server","内存授权","RESOURCE_SEMAPHORE","执行计划","性能优化"]',
 '<h2>问题特征</h2><p>Memory Grants Pending 持续大于 0，通常表示并发查询申请的执行内存超过可用额度，常伴随 RESOURCE_SEMAPHORE 等待。</p><h2>排查步骤</h2><ol><li>核对 grants_pending、总/目标内存和 PLE 趋势。</li><li>从 Top SQL 查找大排序、大哈希、估算行数偏差明显的语句。</li><li>检查 max server memory 是否为操作系统和其他服务保留了足够空间。</li></ol><h2>建议</h2><p>优先修正统计信息、执行计划与并发模型；不要在未评估并发总量前直接放大查询内存或服务器内存参数。</p>'),
('SQL Server 阻塞、长请求与死锁排查','sqlserver','["SQL Server","阻塞","长请求","死锁","故障诊断"]',
 '<h2>区分三类问题</h2><ul><li>长请求：运行时间长但不一定阻塞他人。</li><li>持续阻塞：阻塞链根会话持锁，多个会话排队。</li><li>死锁：SQL Server 已选择牺牲者回滚，需要复盘死锁图。</li></ul><h2>排查步骤</h2><ol><li>先查看平台阻塞现场，找到阻塞链根部、等待类型、数据库和语句摘要。</li><li>结合最长请求、锁等待和死锁事件确认影响范围。</li><li>核对应用事务边界、访问对象顺序和索引是否导致锁范围扩大。</li></ol><h2>建议</h2><p>统一加锁顺序、缩短事务、优化扫描范围。终止会话会回滚事务，只能在确认业务影响后由 DBA 人工执行。</p>'),
('SQL Server I/O 延迟与等待统计联合分析','sqlserver','["SQL Server","I/O","PAGEIOLATCH","WRITELOG","等待统计"]',
 '<h2>联合判定</h2><p>文件读写延迟需与 I/O 等待、IOPS、Top SQL 物理读写及主机磁盘同时分析。单个低流量采样窗口的平均延迟不代表持续故障。</p><h2>排查步骤</h2><ol><li>区分数据文件读取延迟与日志写入延迟。</li><li>PAGEIOLATCH 高时查物理读大户和缓存压力；WRITELOG 高时查日志盘、提交频率与大事务。</li><li>核对同一时段主机磁盘延迟、队列与存储变更。</li></ol><h2>建议</h2><p>先优化读写放大和高消耗 SQL，再评估文件布局或存储能力；不要仅凭一次峰值迁移文件。</p>'),
('SQL Server 事务日志空间与复用受阻排查','sqlserver','["SQL Server","事务日志","log_reuse_wait_desc","日志备份","故障诊断"]',
 '<h2>风险说明</h2><p>日志使用率高只是结果，log_reuse_wait_desc 才是定位入口。常见原因包括缺少日志备份、长事务、AG/复制积压和活动备份。</p><h2>排查步骤</h2><ol><li>核对日志使用率、复用受阻、最长事务和日志备份年龄。</li><li>查看各数据库 recovery_model_desc 与 log_reuse_wait_desc。</li><li>若为 AVAILABILITY_REPLICA 或 REPLICATION，继续检查发送、重做或分发链路。</li></ol><h2>建议</h2><p>按恢复目标恢复备份链路并处理根因。直接收缩日志不能解决复用受阻，还可能造成反复增长和碎片。</p>'),
('SQL Server Always On 健康与延迟排查','sqlserver','["SQL Server","Always On","可用组","发送队列","重做队列","高可用"]',
 '<h2>分层排查</h2><p>先看副本是否连接，再看数据库同步健康和数据移动状态，最后用发送/重做队列及估算秒数判断积压位置。</p><h2>常见原因</h2><ul><li>发送积压：主库日志生成过快、网络带宽或副本连接异常。</li><li>重做积压：辅助副本 CPU/I/O 不足、长查询与重做竞争。</li><li>数据移动暂停：人工暂停、磁盘/日志问题或数据库状态异常。</li></ul><h2>建议</h2><p>先保留现场并确认同步模式、RPO/RTO 和副本角色。平台只提供诊断建议，不自动恢复数据移动或触发故障转移。</p>'),
('SQL Server 备份覆盖、日志传送与恢复验证','sqlserver','["SQL Server","备份恢复","日志传送","RPO","RTO","恢复演练"]',
 '<h2>三个不同问题</h2><ul><li>备份覆盖：每个用户库是否存在完整备份和必要的日志备份。</li><li>备份新鲜度：最近备份是否满足业务 RPO。</li><li>可恢复性：备份是否在隔离环境完成过恢复与校验。</li></ul><h2>排查步骤</h2><ol><li>核对未覆盖数据库、完整备份年龄和日志备份年龄。</li><li>日志传送按备份、复制、还原三阶段定位延迟。</li><li>检查恢复演练登记、实际 RTO 与验证结果。</li></ol><h2>建议</h2><p>备份历史不等于可恢复。应按业务等级设定 RPO/RTO，定期在隔离环境人工执行恢复演练并保留报告。</p>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 3. 多指标综合场景：场景默认停用，由实例级配置人工启用 ----
INSERT INTO monitor_scenario
(scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
 condition_config, recovery_config, diagnosis_template, diagnosis_branches,
 scan_interval_min, builtin)
VALUES
('scenario.sqlserver_cpu_worker_pressure','SQL Server CPU 与 Worker 压力',
 'CPU 调度排队与 CPU 等待同时持续升高，过滤单次任务突发造成的噪音','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"AND","duration":300,"children":[{"type":"condition","code":"runnable","name":"等待 CPU 调度任务","metricCode":"sqlserver.scheduler.runnable_tasks","condType":"threshold","operator":">=","threshold":4,"unit":"count","exprText":"≥ 4"},{"type":"condition","code":"cpu_wait","name":"CPU 等待耗时速率","metricCode":"sqlserver.wait.cpu.ms_per_sec","condType":"threshold","operator":">=","threshold":500,"unit":"ms/s","exprText":"≥ 500ms/s"}]}'::jsonb,
 '{"duration":180}'::jsonb,
 'CPU 调度队列与 CPU 等待持续升高，优先检查高 CPU Top SQL、并行查询、统计信息及近期发布',NULL,1,TRUE),
('scenario.sqlserver_memory_grant_pressure','SQL Server 查询内存压力',
 '内存授权等待与内存类等待同时出现，指向高内存查询或并发内存争用','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"AND","duration":300,"children":[{"type":"condition","code":"grant_pending","name":"等待内存授权请求","metricCode":"sqlserver.memory.grants_pending","condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"≥ 1"},{"type":"condition","code":"memory_wait","name":"内存等待耗时速率","metricCode":"sqlserver.wait.memory.ms_per_sec","condType":"threshold","operator":">=","threshold":100,"unit":"ms/s","exprText":"≥ 100ms/s"}]}'::jsonb,
 '{"duration":180}'::jsonb,
 '查询持续等待执行内存，检查大排序/哈希 SQL、估算偏差、并发量及 max server memory 配置',NULL,1,TRUE),
('scenario.sqlserver_lock_contention','SQL Server 阻塞与长事务风险',
 '被阻塞会话持续存在，并伴随锁等待或长请求，优先识别阻塞链根部','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"AND","duration":180,"children":[{"type":"condition","code":"blocked","name":"被阻塞会话","metricCode":"sqlserver.blocked_sessions","condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"≥ 1"},{"type":"group","logic":"OR","children":[{"type":"condition","code":"lock_wait","name":"锁等待耗时速率","metricCode":"sqlserver.wait.lock.ms_per_sec","condType":"threshold","operator":">=","threshold":100,"unit":"ms/s","exprText":"≥ 100ms/s"},{"type":"condition","code":"long_request","name":"最长请求时长","metricCode":"sqlserver.request.max_seconds","condType":"threshold","operator":">=","threshold":300,"unit":"s","exprText":"≥ 300s"}]}]}'::jsonb,
 '{"duration":180}'::jsonb,
 '检测到持续阻塞，先从阻塞现场定位阻塞链根部，再核对事务边界、等待类型与相关 SQL',
 '[{"when":["long_request"],"text":"阻塞伴随长请求，可能是长事务持锁或大范围扫描；终止会话前必须确认回滚成本和业务影响"}]'::jsonb,1,TRUE),
('scenario.sqlserver_io_bottleneck','SQL Server 数据文件 I/O 瓶颈',
 '文件延迟与 I/O 等待同时升高，避免只按单次平均延迟判断存储故障','level_2',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"AND","duration":300,"children":[{"type":"group","logic":"OR","children":[{"type":"condition","code":"read_latency","name":"平均读取延迟","metricCode":"sqlserver.io.read_latency_ms","condType":"threshold","operator":">=","threshold":20,"unit":"ms","exprText":"≥ 20ms"},{"type":"condition","code":"write_latency","name":"平均写入延迟","metricCode":"sqlserver.io.write_latency_ms","condType":"threshold","operator":">=","threshold":20,"unit":"ms","exprText":"≥ 20ms"}]},{"type":"condition","code":"io_wait","name":"I/O 等待耗时速率","metricCode":"sqlserver.wait.io.ms_per_sec","condType":"threshold","operator":">=","threshold":500,"unit":"ms/s","exprText":"≥ 500ms/s"}]}'::jsonb,
 '{"duration":180}'::jsonb,
 '文件延迟与 I/O 等待持续升高，请区分数据读、数据写和日志写，并关联 Top SQL 与主机磁盘趋势',NULL,1,TRUE),
('scenario.sqlserver_log_pressure','SQL Server 事务日志压力',
 '日志空间偏高、复用受阻或日志等待任一持续出现，综合备份与事务证据定位根因','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"OR","duration":300,"children":[{"type":"condition","code":"log_usage","name":"日志使用率","metricCode":"sqlserver.storage.log_used_percent","condType":"threshold","operator":">=","threshold":85,"unit":"%","exprText":"≥ 85%"},{"type":"condition","code":"reuse_blocked","name":"日志复用受阻","metricCode":"sqlserver.storage.log_reuse_blocked","condType":"threshold","operator":">","threshold":0,"unit":"count","exprText":"> 0"},{"type":"condition","code":"log_wait","name":"日志等待耗时速率","metricCode":"sqlserver.wait.log.ms_per_sec","condType":"threshold","operator":">=","threshold":500,"unit":"ms/s","exprText":"≥ 500ms/s"}]}'::jsonb,
 '{"duration":180}'::jsonb,
 '事务日志出现持续压力，按 log_reuse_wait_desc 核对日志备份、长事务、AG/复制积压和日志盘性能',NULL,1,TRUE),
('scenario.sqlserver_ag_health','SQL Server Always On 健康风险',
 '副本断连、同步异常、数据移动暂停或发送/重做积压任一持续出现','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"OR","duration":180,"children":[{"type":"condition","code":"disconnected","name":"断连副本","metricCode":"sqlserver.ag.disconnected_replicas","condType":"threshold","operator":">","threshold":0,"unit":"count","exprText":"> 0"},{"type":"condition","code":"unhealthy","name":"非健康数据库","metricCode":"sqlserver.ag.unhealthy_databases","condType":"threshold","operator":">","threshold":0,"unit":"count","exprText":"> 0"},{"type":"condition","code":"suspended","name":"数据移动暂停","metricCode":"sqlserver.ag.suspended_databases","condType":"threshold","operator":">","threshold":0,"unit":"count","exprText":"> 0"},{"type":"condition","code":"send_lag","name":"发送积压估算","metricCode":"sqlserver.ag.max_send_seconds","condType":"threshold","operator":">","threshold":300,"unit":"s","exprText":"> 300s"},{"type":"condition","code":"redo_lag","name":"重做积压估算","metricCode":"sqlserver.ag.max_redo_seconds","condType":"threshold","operator":">","threshold":300,"unit":"s","exprText":"> 300s"}]}'::jsonb,
 '{"duration":300}'::jsonb,
 'Always On 健康或延迟异常，按连接、同步健康、数据移动、发送队列和重做队列分层定位；不自动故障转移',NULL,1,TRUE),
('scenario.sqlserver_backup_readiness','SQL Server 备份与恢复准备度风险',
 '备份未覆盖、备份逾期或日志传送延迟任一命中，提醒核对 RPO/RTO 和恢复验证','level_1',
 (SELECT id FROM database_type WHERE code='SQLSERVER'),NULL,
 '{"logic":"OR","duration":0,"children":[{"type":"condition","code":"uncovered","name":"未覆盖数据库","metricCode":"sqlserver.backup.uncovered_database_count","condType":"threshold","operator":">","threshold":0,"unit":"count","exprText":"> 0"},{"type":"condition","code":"log_missing","name":"缺少日志备份数据库","metricCode":"sqlserver.backup.log_missing_database_count","condType":"threshold","operator":">","threshold":0,"unit":"count","exprText":"> 0"},{"type":"condition","code":"full_age","name":"完整备份年龄","metricCode":"sqlserver.backup.max_full_age_hours","condType":"threshold","operator":">","threshold":24,"unit":"h","exprText":"> 24h"},{"type":"condition","code":"log_age","name":"日志备份年龄","metricCode":"sqlserver.backup.max_log_age_minutes","condType":"threshold","operator":">","threshold":60,"unit":"min","exprText":"> 60min"},{"type":"condition","code":"shipping_delay","name":"日志传送还原延迟","metricCode":"sqlserver.log_shipping.restore_delay_minutes","condType":"threshold","operator":">","threshold":30,"unit":"min","exprText":"> 30min"}]}'::jsonb,
 '{"duration":300}'::jsonb,
 '备份覆盖或新鲜度不满足默认目标；请按业务等级核对 RPO/RTO，并确认最近一次隔离恢复演练结果',NULL,5,TRUE)
ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 4. 场景关联知识文章 ----
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server CPU 与 Worker 排队排查指南') WHERE s.scenario_code='scenario.sqlserver_cpu_worker_pressure';
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server 查询内存授权等待排查') WHERE s.scenario_code='scenario.sqlserver_memory_grant_pressure';
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server 阻塞、长请求与死锁排查') WHERE s.scenario_code='scenario.sqlserver_lock_contention';
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server I/O 延迟与等待统计联合分析') WHERE s.scenario_code='scenario.sqlserver_io_bottleneck';
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server 事务日志空间与复用受阻排查') WHERE s.scenario_code='scenario.sqlserver_log_pressure';
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server Always On 健康与延迟排查') WHERE s.scenario_code='scenario.sqlserver_ag_health';
UPDATE monitor_scenario s SET knowledge_article_ids=(SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id),'[]'::jsonb) FROM knowledge_article k WHERE k.title='SQL Server 备份覆盖、日志传送与恢复验证') WHERE s.scenario_code='scenario.sqlserver_backup_readiness';

-- ---- 5. SQL Server 事件下钻画像：让规则/场景可直接进入证据、原因和知识库链路 ----
INSERT INTO alert_drilldown_profile
(profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT v.profile_code,v.profile_label,'sqlserver',v.match_rules::jsonb,v.related_metrics::jsonb,
       v.causes::jsonb,v.steps::jsonb,v.actions::jsonb,TRUE,TRUE,v.sort,v.remark
FROM (VALUES
('sqlserver_runtime','SQL Server 运行与锁',
 '[{"matchType":"prefix","pattern":"sqlserver.scheduler."},{"matchType":"prefix","pattern":"sqlserver.memory."},{"matchType":"exact","pattern":"sqlserver.blocked_sessions"},{"matchType":"prefix","pattern":"sqlserver.request."},{"matchType":"exact","pattern":"sqlserver.deadlocks_per_sec"},{"matchType":"prefix","pattern":"scenario.sqlserver_cpu_"},{"matchType":"prefix","pattern":"scenario.sqlserver_memory_"},{"matchType":"prefix","pattern":"scenario.sqlserver_lock_"}]',
 '[{"code":"sqlserver.scheduler.runnable_tasks","label":"等待 CPU 调度任务","unit":"","color":"#E5484D"},{"code":"sqlserver.memory.grants_pending","label":"等待内存授权","unit":"","color":"#E08600"},{"code":"sqlserver.blocked_sessions","label":"被阻塞会话","unit":"","color":"#9B59B6"},{"code":"sqlserver.request.max_seconds","label":"最长请求时长","unit":"s","color":"#6366F1"}]',
 '[{"cause":"高消耗 SQL 或并发突增","confidence":0.65,"color":"warning","evidence":["当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","结合 Top SQL 与等待分类确认 CPU、内存或锁争用"]},{"cause":"长事务持锁或应用事务边界异常","confidence":0.6,"color":"danger","evidence":["被阻塞会话与最长请求同步上升时优先定位阻塞链根部"]}]',
 '[{"title":"查看性能分析","description":"核对等待分类、CPU、内存与 I/O 的同一时间窗趋势","action":"查看性能分析","link":"performance"},{"title":"查看 Top SQL","description":"按 CPU、耗时和物理读定位高消耗语句","action":"查看 Top SQL","link":"slowsql"},{"title":"查阅 SQL Server 排查指南","description":"按告警类型查看只读核查步骤和人工处置边界","action":"查看知识库","link":"knowledge"}]',
 '[{"action":"查看当前请求与阻塞关系","risk":"low","description":"定位活跃请求及阻塞源，不执行终止操作","sql":"SELECT session_id, blocking_session_id, status, wait_type, wait_time, total_elapsed_time FROM sys.dm_exec_requests WHERE session_id <> @@SPID ORDER BY total_elapsed_time DESC;","impact":"只读 DMV 查询"}]',20,'SQL Server CPU、内存、长请求、阻塞和死锁告警/场景'),
('sqlserver_storage','SQL Server I/O 与事务日志',
 '[{"matchType":"prefix","pattern":"sqlserver.io."},{"matchType":"prefix","pattern":"sqlserver.storage.log_"},{"matchType":"exact","pattern":"scenario.sqlserver_io_bottleneck"},{"matchType":"exact","pattern":"scenario.sqlserver_log_pressure"}]',
 '[{"code":"sqlserver.io.read_latency_ms","label":"平均读取延迟","unit":"ms","color":"#E5484D"},{"code":"sqlserver.io.write_latency_ms","label":"平均写入延迟","unit":"ms","color":"#E08600"},{"code":"sqlserver.storage.log_used_percent","label":"日志使用率","unit":"%","color":"#6366F1"},{"code":"sqlserver.storage.log_reuse_blocked","label":"日志复用受阻","unit":"","color":"#9B59B6"}]',
 '[{"cause":"高物理读写负载或存储延迟","confidence":0.65,"color":"warning","evidence":["文件延迟需与 I/O 等待和主机磁盘同窗确认"]},{"cause":"日志备份、长事务或 AG/复制阻止日志截断","confidence":0.65,"color":"danger","evidence":["日志使用率高且复用受阻时按 log_reuse_wait_desc 定位"]}]',
 '[{"title":"查看性能分析","description":"关联文件延迟、等待统计、IOPS 与主机磁盘","action":"查看性能分析","link":"performance"},{"title":"查看 Top SQL","description":"定位物理读取或写入大户","action":"查看 Top SQL","link":"slowsql"},{"title":"查阅日志与 I/O 指南","description":"按复用等待和等待类型分支排查","action":"查看知识库","link":"knowledge"}]',
 '[{"action":"查看数据库日志空间与复用等待","risk":"low","description":"确认各库日志使用和截断阻塞原因","sql":"SELECT name, recovery_model_desc, log_reuse_wait_desc FROM sys.databases ORDER BY name;","impact":"只读系统目录查询"}]',21,'SQL Server 文件延迟、I/O 等待与事务日志告警/场景'),
('sqlserver_ha','SQL Server Always On',
 '[{"matchType":"prefix","pattern":"sqlserver.ag."},{"matchType":"exact","pattern":"scenario.sqlserver_ag_health"}]',
 '[{"code":"sqlserver.ag.disconnected_replicas","label":"断连副本","unit":"","color":"#E5484D"},{"code":"sqlserver.ag.unhealthy_databases","label":"非健康数据库","unit":"","color":"#B91C1C"},{"code":"sqlserver.ag.max_send_seconds","label":"发送积压估算","unit":"s","color":"#E08600"},{"code":"sqlserver.ag.max_redo_seconds","label":"重做积压估算","unit":"s","color":"#6366F1"}]',
 '[{"cause":"副本连接或网络异常","confidence":0.7,"color":"danger","evidence":["断连副本或同步健康异常持续存在"]},{"cause":"主库日志生成超过发送能力","confidence":0.6,"color":"warning","evidence":["发送队列与估算发送秒数持续增加"]},{"cause":"辅助副本重做能力不足","confidence":0.55,"color":"warning","evidence":["重做队列持续增加，检查辅助副本 CPU、I/O 与长查询"]}]',
 '[{"title":"确认副本与数据库状态","description":"按连接、同步健康、数据移动状态分层判断","action":"查看性能分析","link":"performance"},{"title":"核对发送和重做积压","description":"判断瓶颈位于主库发送、网络还是辅助副本重做","action":"查看知识库","link":"knowledge"}]',
 '[{"action":"查看本地可见 AG 副本状态","risk":"low","description":"读取副本连接、角色及同步健康状态","sql":"SELECT ar.replica_server_name, ars.role_desc, ars.connected_state_desc, ars.synchronization_health_desc FROM sys.dm_hadr_availability_replica_states ars JOIN sys.availability_replicas ar ON ar.replica_id=ars.replica_id;","impact":"只读 Always On DMV 查询"}]',22,'SQL Server Always On 健康与延迟告警/场景'),
('sqlserver_backup','SQL Server 备份与日志传送',
 '[{"matchType":"prefix","pattern":"sqlserver.backup."},{"matchType":"prefix","pattern":"sqlserver.log_shipping."},{"matchType":"exact","pattern":"scenario.sqlserver_backup_readiness"}]',
 '[{"code":"sqlserver.backup.max_full_age_hours","label":"完整备份最大年龄","unit":"h","color":"#E5484D"},{"code":"sqlserver.backup.max_log_age_minutes","label":"日志备份最大年龄","unit":"min","color":"#E08600"},{"code":"sqlserver.backup.uncovered_database_count","label":"未覆盖数据库","unit":"","color":"#B91C1C"},{"code":"sqlserver.log_shipping.restore_delay_minutes","label":"日志传送还原延迟","unit":"min","color":"#6366F1"}]',
 '[{"cause":"备份作业失败或策略未覆盖新库","confidence":0.7,"color":"danger","evidence":["未覆盖数据库或备份年龄超过默认目标"]},{"cause":"日志传送链路某阶段延迟","confidence":0.6,"color":"warning","evidence":["按备份、复制、还原三阶段定位延迟"]}]',
 '[{"title":"核对备份覆盖与新鲜度","description":"确认每个用户库的恢复模式、最近完整/日志备份时间","action":"查看知识库","link":"knowledge"},{"title":"核对恢复演练","description":"备份历史不能替代隔离恢复验证，检查最近演练结果和实际 RTO","action":"查看知识库","link":"knowledge"}]',
 '[{"action":"查看最近备份时间","risk":"low","description":"按用户库汇总最近完整与日志备份","sql":"SELECT d.name, MAX(CASE WHEN b.type=''D'' THEN b.backup_finish_date END) AS last_full, MAX(CASE WHEN b.type=''L'' THEN b.backup_finish_date END) AS last_log FROM sys.databases d LEFT JOIN msdb.dbo.backupset b ON b.database_name=d.name WHERE d.database_id>4 GROUP BY d.name;","impact":"只读查询 msdb 备份历史"}]',23,'SQL Server 备份覆盖、新鲜度、日志传送和恢复准备度'),
('sqlserver_operations','SQL Server 作业与配置',
 '[{"matchType":"prefix","pattern":"sqlserver.agent."},{"matchType":"prefix","pattern":"sqlserver.configuration."}]',
 '[{"code":"sqlserver.agent.failed_jobs","label":"最近失败作业","unit":"","color":"#E5484D"},{"code":"sqlserver.agent.disabled_jobs","label":"停用作业","unit":"","color":"#E08600"},{"code":"sqlserver.configuration.auto_shrink_databases","label":"启用自动收缩数据库","unit":"","color":"#6366F1"}]',
 '[{"cause":"作业执行失败或基础设施不可用","confidence":0.65,"color":"danger","evidence":["先读取作业历史错误摘要，再核对代理服务、权限和目标资源"]},{"cause":"历史遗留配置带来碎片与 I/O 抖动","confidence":0.55,"color":"warning","evidence":["auto shrink 启用只代表风险，需要结合业务窗口人工评估"]}]',
 '[{"title":"查看作业历史","description":"确认失败步骤、时间和错误摘要，不读取敏感作业命令文本","action":"查看知识库","link":"knowledge"},{"title":"核对配置快照","description":"与基线和变更记录比对，人工评审后再修改","action":"查看性能分析","link":"performance"}]',
 '[{"action":"查看最近失败作业","risk":"low","description":"读取作业名称、最近运行日期和状态","sql":"SELECT j.name, h.run_date, h.run_time, h.run_status, h.message FROM msdb.dbo.sysjobs j JOIN msdb.dbo.sysjobhistory h ON h.job_id=j.job_id WHERE h.step_id=0 AND h.run_status=0 ORDER BY h.instance_id DESC;","impact":"只读查询 msdb 作业历史"}]',24,'SQL Server Agent 作业与关键配置风险'),
('sqlserver_generic','SQL Server 通用',
 '[{"matchType":"prefix","pattern":"sqlserver."},{"matchType":"prefix","pattern":"scenario.sqlserver_"}]',
 '[{"code":"sqlserver.availability","label":"实例可达性","unit":"","color":"#E5484D"},{"code":"sqlserver.request.active","label":"活跃请求","unit":"","color":"#0C7C97"},{"code":"sqlserver.blocked_sessions","label":"被阻塞会话","unit":"","color":"#E08600"},{"code":"sqlserver.storage.log_used_percent","label":"日志使用率","unit":"%","color":"#6366F1"}]',
 '[{"cause":"实例、网络或采集权限异常","confidence":0.6,"color":"danger","evidence":["可达性为 0 或全部指标断档时先检查服务、网络和采集日志"]},{"cause":"指标偏离正常范围","confidence":0.5,"color":"warning","evidence":["结合异常起始时间、发布变更和相关指标趋势判断"]}]',
 '[{"title":"查看采集日志","description":"确认连接失败或权限不足的面向用户错误信息","action":"查看采集任务","link":"collector"},{"title":"查看性能分析","description":"从核心健康、等待统计和 Top SQL 确认影响范围","action":"查看性能分析","link":"performance"},{"title":"查阅 SQL Server 知识库","description":"按告警标签检索对应排查文章","action":"查看知识库","link":"knowledge"}]',
 '[{"action":"验证 SQL Server 基本状态","risk":"low","description":"确认版本、启动时间和当前连接库","sql":"SELECT @@SERVERNAME AS server_name, @@VERSION AS version, DB_NAME() AS database_name, sqlserver_start_time FROM sys.dm_os_sys_info;","impact":"只读查询"}]',29,'SQL Server 未被更具体画像匹配的指标与场景兜底')
) AS v(profile_code,profile_label,match_rules,related_metrics,causes,steps,actions,sort,remark)
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile p WHERE p.profile_code=v.profile_code);
