-- =============================================================
-- V154：PostgreSQL 支持（二期 A）——场景 / 下钻画像 / 知识库 / 场景菜单
--   前置：V152 PG 类型/版本/pg.* 指标/内置规则/菜单，V153 PG 下钻菜单。
--   本脚本补齐 PG 实例的事件分析与场景联动链路：
--   1. 知识库：PG 排查文章 6 篇（长事务与膨胀 / idle in transaction /
--      复制延迟 / 死锁 / 连接耗尽 / 缓存与临时文件）
--   2. 内置场景 4 个（db_type_id 指向 POSTGRESQL，场景引擎按实例类型自动过滤）：
--        scenario.pg_connection_exhaustion  连接耗尽风险（使用率高+活跃高+TPS 未涨）
--        scenario.pg_long_trx_bloat         长事务与膨胀风险（长事务/事务中空闲，任一命中）
--        scenario.pg_lock_contention        锁竞争（锁等待/被阻塞/死锁，任一命中）
--        scenario.pg_replication_risk       复制延迟风险（从库延迟持续升高）
--   3. 下钻画像 6 个（pg.* 指标前缀 + scenario.pg_ 场景编码匹配，
--      PG 事件不再落 MySQL 通用画像）
--   4. PG 分组新增「场景管理」菜单（复用通用场景页，component monitor/pg/scenario）
--   5. 场景关联知识库文章（按标题回查 id）
-- 注：全脚本幂等（按标题/编码防重）。
-- =============================================================

-- ---- 1. 知识库文章 ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES

  -- 1.1 长事务与表膨胀（配套 builtin.pg.trx.long / scenario.pg_long_trx_bloat）
  ('PostgreSQL 长事务与表膨胀排查指南', 'fault', '["PostgreSQL","长事务","表膨胀","VACUUM","故障诊断"]',
   '<h2>为什么 PG 的长事务危害远大于 MySQL</h2><p>PostgreSQL 的 MVCC 把旧版本行留在原表内，依赖 VACUUM 清理。任何一个长事务（含只读事务）都会钉住全库的清理水位：VACUUM 无法回收比该事务更新的死元组，表和索引持续膨胀，查询越来越慢，极端情况下还会拖住事务号回收。</p><h2>排查步骤</h2><ol><li><strong>找出最老事务</strong>：<code>SELECT pid, state, xact_start, now()-xact_start AS dur, left(query,80) FROM pg_stat_activity WHERE xact_start IS NOT NULL ORDER BY xact_start LIMIT 10;</code></li><li><strong>区分形态</strong>：state=active 是真在跑的长 SQL；state=idle in transaction 是拿着事务不提交（危害相同，参见《PostgreSQL 事务中空闲连接治理》）。</li><li><strong>确认膨胀程度</strong>：<code>SELECT relname, n_live_tup, n_dead_tup, round(n_dead_tup*100.0/nullif(n_live_tup+n_dead_tup,0),1) AS dead_pct, last_autovacuum FROM pg_stat_user_tables ORDER BY n_dead_tup DESC LIMIT 20;</code> dead_pct 超过 20% 需要关注。</li><li><strong>确认 VACUUM 是否被钉住</strong>：长事务未结束前，即使手动 VACUUM 也无法回收其后产生的死元组。</li></ol><h2>处置建议</h2><ul><li>推动应用侧提交或回滚长事务；确认无业务影响后可由 DBA 执行 <code>SELECT pg_cancel_backend(pid)</code>（取消语句）或 <code>pg_terminate_backend(pid)</code>（断开连接），须人工确认。</li><li>治理性配置：设置 <code>idle_in_transaction_session_timeout</code>（如 5~10 分钟）与 <code>statement_timeout</code>。</li><li>膨胀严重的表在低峰期 <code>VACUUM (VERBOSE, ANALYZE)</code>；空间需要归还操作系统时评估 pg_repack（在线）或 VACUUM FULL（锁表，谨慎）。</li></ul>'),

  -- 1.2 idle in transaction（配套 builtin.pg.idle_in_trx）
  ('PostgreSQL 事务中空闲（idle in transaction）连接治理', 'fault', '["PostgreSQL","idle in transaction","连接管理","故障诊断"]',
   '<h2>问题特征</h2><p>连接开启了事务却长时间不提交也不回滚（pg_stat_activity 中 state=idle in transaction）。它同时带来三重危害：持有的锁阻塞其他会话、钉住 VACUUM 水位引发表膨胀、占用连接名额。绝大多数由应用代码缺陷引起：拿到连接开事务后走了慢逻辑、异常路径漏了 commit/rollback、ORM 配置不当。</p><h2>排查步骤</h2><ol><li><strong>定位空闲事务来源</strong>：<code>SELECT pid, usename, client_addr, application_name, now()-state_change AS idle_dur, left(query,80) AS last_query FROM pg_stat_activity WHERE state=''idle in transaction'' ORDER BY state_change LIMIT 10;</code> last_query 是该事务最后执行的语句，是定位应用代码位置的关键线索。</li><li><strong>确认是否持锁阻塞他人</strong>：结合平台锁等待指标或 <code>pg_blocking_pids(pid)</code> 确认阻塞关系。</li><li><strong>联系应用负责人</strong>：按 client_addr + application_name 找到归属服务，检查事务边界代码。</li></ol><h2>处置建议</h2><ul><li>紧急：确认无业务影响后 <code>SELECT pg_terminate_backend(pid);</code> 断开元凶连接（事务会回滚，须人工确认）。</li><li>根治：应用修复事务边界；数据库侧设置 <code>idle_in_transaction_session_timeout = ''300s''</code> 作为兜底，超时连接自动断开。</li><li>连接池（如 PgBouncer 事务模式）可从架构上避免应用长期占用事务。</li></ul>'),

  -- 1.3 复制延迟（配套 builtin.pg.repl.delay.critical / scenario.pg_replication_risk）
  ('PostgreSQL 流复制延迟排查方法', 'fault', '["PostgreSQL","流复制","复制延迟","高可用","故障诊断"]',
   '<h2>延迟的三段拆解</h2><p>流复制链路分为：主库发送（sent_lsn）→ 从库写入/刷盘（write/flush_lsn）→ 从库回放（replay_lsn）。在主库执行 <code>SELECT application_name, client_addr, pg_wal_lsn_diff(pg_current_wal_lsn(), sent_lsn) AS send_lag, pg_wal_lsn_diff(sent_lsn, flush_lsn) AS flush_lag, pg_wal_lsn_diff(flush_lsn, replay_lsn) AS replay_lag FROM pg_stat_replication;</code> 看差值落在哪一段。</p><h2>按段定位</h2><ul><li><strong>send_lag 大</strong>：主库 WAL 产生速度超过网络发送能力——检查大事务/批量写入、网络带宽。</li><li><strong>flush_lag 大</strong>：从库磁盘写入慢——检查从库 IO 能力。</li><li><strong>replay_lag 大</strong>（最常见）：从库单进程回放跟不上——主库大事务、DDL、从库上有长查询与回放冲突（表现为回放暂停）。</li></ul><h2>注意事项</h2><ul><li>平台的复制回放延迟按「最后回放事务距今」估算：主库长时间无写入时该值自然增长，需结合主库 TPS 判断是否真延迟。</li><li>从库长查询与回放冲突时，PG 默认取消查询（可调 <code>max_standby_streaming_delay</code>）；把从库当报表库用时尤其常见。</li><li>从库掉线后主库若配置了复制槽，WAL 会持续堆积占满磁盘，参见复制槽监控（二期规划）。</li></ul><h2>处置建议</h2><ul><li>短期：错峰批量写入，拆分大事务；从库报表查询迁移或限时。</li><li>长期：评估从库磁盘/CPU 规格；PG15+ 可评估 recovery_prefetch 提升回放效率。</li></ul>'),

  -- 1.4 死锁（配套 builtin.pg.deadlock / scenario.pg_lock_contention）
  ('PostgreSQL 死锁定位与预防', 'fault', '["PostgreSQL","死锁","锁竞争","故障诊断"]',
   '<h2>机制说明</h2><p>两个（或多个）事务互相等待对方持有的锁超过 <code>deadlock_timeout</code>（默认 1s）时，PG 死锁检测器会自动回滚其中一个事务并报错 <code>deadlock detected</code>。业务侧表现为随机的事务失败，pg_stat_database.deadlocks 计数增加。</p><h2>定位步骤</h2><ol><li><strong>查数据库日志</strong>：死锁发生时 PG 会在服务器日志记录完整的两个事务的语句与锁信息（Process X waits for ShareLock on transaction ...），这是最直接的现场证据，需要主机上查看 postgresql.log。</li><li><strong>复盘加锁顺序</strong>：典型死锁是两个事务以相反顺序更新同一批行（A 先改行1再改行2，B 先改行2再改行1）。</li><li><strong>识别隐性锁</strong>：外键约束会对被引用行加锁；SELECT FOR UPDATE 范围过大；批量 UPDATE 无固定排序，都是常见诱因。</li></ol><h2>预防建议</h2><ul><li>统一加锁顺序：批量更新按主键排序（ORDER BY id）后执行。</li><li>缩短事务：事务内不做外部调用与人工等待。</li><li>降低隔离级别不必要的范围锁；热点计数器行考虑拆分或改队列。</li><li>频发时开启 <code>log_lock_waits = on</code> 记录锁等待细节辅助定位。</li></ul>'),

  -- 1.5 连接耗尽（配套 builtin.pg.conn.usage.critical / scenario.pg_connection_exhaustion）
  ('PostgreSQL 连接耗尽与连接池配置', 'practice', '["PostgreSQL","连接管理","连接池","PgBouncer","最佳实践"]',
   '<h2>PG 连接模型的特殊性</h2><p>PostgreSQL 每个连接是一个独立进程（MySQL 是线程），连接成本显著更高：大量空闲连接同样消耗内存与调度开销。max_connections 不宜盲目调大——几百个活跃连接就足以打满多数服务器，正确解法几乎总是连接池。</p><h2>连接打满时的排查</h2><ol><li><strong>看连接构成</strong>：<code>SELECT state, count(*) FROM pg_stat_activity GROUP BY state;</code> active 多是负载问题；idle 多是应用连接池配置过大或泄漏；idle in transaction 多是事务边界缺陷。</li><li><strong>按来源统计</strong>：<code>SELECT client_addr, application_name, count(*) FROM pg_stat_activity GROUP BY 1,2 ORDER BY 3 DESC;</code> 定位连接大户应用。</li><li><strong>确认是否慢 SQL 堆积</strong>：活跃连接高且 TPS 未涨，通常是语句变慢导致连接占用时间拉长，转向慢 SQL 排查。</li></ol><h2>治理建议</h2><ul><li>为管理员保留后门：<code>superuser_reserved_connections</code>（默认 3）确保打满时 DBA 仍能登录处置。</li><li>应用侧连接池上限 = 各服务池大小之和应低于 max_connections 的 80%。</li><li>服务数多时引入 PgBouncer（transaction 模式）做集中连接池，数据库真实连接数可降一个数量级。</li></ul>'),

  -- 1.6 缓存与临时文件（配套 builtin.pg.cache.hit_rate.low / scenario.pg 性能类）
  ('PostgreSQL 缓存命中率与临时文件优化', 'performance', '["PostgreSQL","shared_buffers","work_mem","临时文件","性能优化"]',
   '<h2>两个高性价比的性能信号</h2><p><strong>缓存命中率</strong>（blks_hit / (blks_hit+blks_read)）反映热数据是否装得进 shared_buffers，健康值一般 ≥ 95%；<strong>临时文件</strong>（temp_files/temp_bytes）是排序、哈希、物化超过 work_mem 后落盘的产物，持续产生说明存在需要优化的大查询或 work_mem 偏小。</p><h2>命中率低的排查</h2><ol><li>确认 shared_buffers 配置：经验起点为物理内存的 25%（PG 还依赖操作系统页缓存，不宜像 MySQL Buffer Pool 那样配到 70%+），同时核对 effective_cache_size 是否如实反映可用缓存总量（影响优化器选择索引）。</li><li>识别大扫描：命中率被单个大表全表扫描拉低很常见，结合 Top SQL（需 pg_stat_statements）定位扫描大户，优先补索引而不是加内存。</li></ol><h2>临时文件的排查</h2><ol><li>定位产生临时文件的语句：开启 <code>log_temp_files = 0</code> 后日志会记录每个临时文件及对应语句；有 pg_stat_statements 时看 temp_blks_written 排名。</li><li>处置顺序：先优化 SQL（补索引消除大排序、改写 DISTINCT/聚合），再考虑调 work_mem——注意 work_mem 是每个排序节点独享，高并发下总消耗 = work_mem × 并发排序数，盲目调大有 OOM 风险；可只对报表类会话 <code>SET work_mem</code>。</li></ol>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 2. 内置场景（db_type_id → POSTGRESQL，场景引擎按实例类型自动过滤） ----

-- [1] 连接耗尽风险（AND）：使用率高 + 活跃连接高 + TPS 未同步上涨 → 泄漏/慢SQL堆积特征
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_connection_exhaustion',
    'PG 连接耗尽风险',
    '综合连接使用率、活跃连接、TPS 三个信号：连接被大量占用但 TPS 未同步上涨，'
    '区分「业务流量正常增长」与「连接泄漏 / 慢SQL堆积」。PG 每连接一个进程，连接打满的代价高于 MySQL',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"AND","duration":120,"children":[
       {"type":"condition","code":"conn_usage","name":"连接使用率","metricCode":"pg.conn.usage",
        "condType":"threshold","operator":">=","threshold":85,"unit":"%","exprText":"≥ 85%"},
       {"type":"condition","code":"conn_active","name":"活跃连接数","metricCode":"pg.conn.active",
        "condType":"threshold","operator":">=","threshold":20,"unit":"count","exprText":"≥ 20"},
       {"type":"condition","code":"tps_flat","name":"TPS 环比","metricCode":"pg.tps",
        "condType":"rate_change","compareOffset":"1h","operator":"<","threshold":20,"unit":"%",
        "exprText":"较1小时前上涨 < 20%（无显著上涨）"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '连接被大量占用但 TPS 未同步上涨，典型连接泄漏或慢 SQL 堆积特征；'
    '建议按 state 分组统计连接构成（active / idle / idle in transaction），并按来源定位连接大户应用',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [2] 长事务与膨胀风险（OR）：长事务 / 事务中空闲任一命中；PG 中两者都会钉住 VACUUM
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_long_trx_bloat',
    'PG 长事务与膨胀风险',
    '长事务与事务中空闲联合监控（任一命中即触发）。PostgreSQL 中任何长事务都会阻止 VACUUM 清理死元组，'
    '引发表膨胀与查询变慢，危害高于 MySQL 同类问题；内置单阈值规则告警后，本场景提供更早的组合预警',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"OR","duration":120,"children":[
       {"type":"condition","code":"long_trx","name":"最长事务时长","metricCode":"pg.trx.max_seconds",
        "condType":"threshold","operator":">=","threshold":1800,"unit":"s","exprText":"≥ 1800s（30分钟）"},
       {"type":"condition","code":"idle_in_trx","name":"事务中空闲最长时长","metricCode":"pg.trx.idle_in_trx_max_seconds",
        "condType":"threshold","operator":">=","threshold":600,"unit":"s","exprText":"≥ 600s（10分钟）"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '存在长时间未结束的事务，VACUUM 无法清理其后产生的死元组，表膨胀风险持续累积；'
    '建议按 xact_start 排序定位最老事务，推动应用提交或回滚，并评估设置 idle_in_transaction_session_timeout',
    '[{"when":["long_trx"],"text":"存在长时间运行的活跃事务：确认是大批量 SQL 还是失控查询，评估取消语句（pg_cancel_backend）"},
      {"when":["idle_in_trx"],"text":"存在事务中空闲连接：典型应用忘记提交，按 client_addr/application_name 定位归属服务修复事务边界"}]'::jsonb,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [3] 锁竞争（OR）：锁等待 / 被阻塞会话 / 死锁任一命中
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_lock_contention',
    'PG 锁竞争',
    '锁等待、被阻塞会话、死锁三个信号联合监控（任一命中即触发），区分偶发锁冲突与系统性锁竞争，'
    '并按命中信号给出差异化排查提示',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"OR","duration":0,"children":[
       {"type":"condition","code":"lock_waits","name":"等待中锁请求","metricCode":"pg.locks.waiting",
        "condType":"threshold","operator":">=","threshold":5,"unit":"count","exprText":"≥ 5"},
       {"type":"condition","code":"blocked","name":"被阻塞会话","metricCode":"pg.blocked_sessions",
        "condType":"threshold","operator":">=","threshold":3,"unit":"count","exprText":"≥ 3"},
       {"type":"condition","code":"deadlock","name":"死锁次数","metricCode":"pg.delta.deadlocks",
        "condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"> 0（本采集周期）"}
     ]}'::jsonb,
    '{"duration":180}'::jsonb,
    '检测到锁竞争信号，建议用 pg_blocking_pids 定位阻塞源会话，确认是否为长事务持锁，'
    '并评估是否需要人工终止阻塞源（须 DBA 确认后执行）',
    '[{"when":["deadlock"],"text":"发生死锁，PG 已自动回滚其中一个事务；请查数据库日志获取死锁现场，复盘两个事务的加锁顺序"},
      {"when":["blocked"],"text":"多个会话被阻塞，大概率存在单一阻塞源（长事务持锁），优先定位并处理阻塞源而不是被阻塞会话"}]'::jsonb,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [4] 复制延迟风险（AND，仅从库命中）：延迟持续升高
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.pg_replication_risk',
    'PG 复制延迟风险',
    '从库回放延迟持续超阈值时触发（主库不产出 lag 指标自然不命中）。'
    '注意主库长时间无写入时回放延迟会自然增长，场景要求持续命中以过滤该噪声',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'POSTGRESQL'),
    NULL,
    '{"logic":"AND","duration":180,"children":[
       {"type":"condition","code":"is_replica","name":"复制角色","metricCode":"pg.repl.is_replica",
        "condType":"threshold","operator":">=","threshold":1,"unit":"","exprText":"= 从库"},
       {"type":"condition","code":"repl_lag","name":"复制回放延迟","metricCode":"pg.repl.lag_seconds",
        "condType":"threshold","operator":">=","threshold":30,"unit":"s","exprText":"≥ 30s"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '从库回放延迟持续偏高：先在主库 pg_stat_replication 按 send/flush/replay 三段定位瓶颈段，'
    '常见原因为主库大事务/批量写入、从库 IO 不足、从库长查询与回放冲突',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 3. 下钻画像（pg.* 前缀 + 场景编码匹配；exact 优先、prefix 长者优先） ----

-- 3.1 PG 连接类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_connections', 'PG 连接类', 'postgresql',
$J$[
  {"matchType":"prefix","pattern":"pg.conn."},
  {"matchType":"exact","pattern":"scenario.pg_connection_exhaustion"}
]$J$::jsonb,
$J$[
  {"code":"pg.conn.usage","label":"连接使用率","unit":"%","color":"#E5484D"},
  {"code":"pg.conn.total","label":"当前连接数","unit":"","color":"#0C7C97"},
  {"code":"pg.conn.active","label":"活跃连接数","unit":"","color":"#6366F1"},
  {"code":"pg.conn.idle_in_trx","label":"事务中空闲连接","unit":"","color":"#E08600"},
  {"code":"pg.tps","label":"TPS","unit":"","color":"#15A36A"}
]$J$::jsonb,
$J$[
  {"cause":"应用连接泄漏或连接池配置过大","confidence":0.7,"color":"danger","evidence":["当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","idle 连接占比高而 TPS 未上涨，指向应用拿了连接不归还或池上限之和超过 max_connections","按 client_addr/application_name 分组统计可定位连接大户"]},
  {"cause":"慢 SQL 堆积拉长连接占用","confidence":0.6,"color":"warning","evidence":["活跃连接多且语句执行变慢时，连接周转率下降造成堆积","结合最长事务时长与锁等待确认是否有阻塞源"]},
  {"cause":"业务流量真实增长","confidence":0.4,"color":"info","evidence":["TPS 与连接数同步上涨属于容量问题而非故障","评估引入 PgBouncer 连接池或提升规格"]}
]$J$::jsonb,
$J$[
  {"title":"查看连接构成","description":"在实时概况查看连接数趋势（当前/活跃/事务中空闲三条线），确认堆积的是哪类连接","action":"查看实时概况","link":"pg_realtime"},
  {"title":"定位连接大户","description":"按来源地址与应用名分组统计连接数，找到归属服务","action":"查看知识库","link":"knowledge"},
  {"title":"检查长事务与锁","description":"确认是否存在长事务/阻塞源拉长连接占用","action":"查看实时概况","link":"pg_realtime"}
]$J$::jsonb,
$J$[
  {"action":"按状态统计连接构成","risk":"low","description":"确认 active / idle / idle in transaction 占比","sql":"SELECT state, count(*) FROM pg_stat_activity GROUP BY state ORDER BY 2 DESC;","impact":"只读查询，无风险"},
  {"action":"按来源定位连接大户","risk":"low","description":"定位连接数异常的应用","sql":"SELECT client_addr, application_name, count(*) FROM pg_stat_activity GROUP BY 1,2 ORDER BY 3 DESC LIMIT 10;","impact":"只读查询，无风险"},
  {"action":"终止泄漏连接（人工确认）","risk":"high","description":"确认连接归属与业务影响后，由 DBA 终止空闲过久的连接","sql":"-- 先确认，再执行\nSELECT pg_terminate_backend(pid) FROM pg_stat_activity\n WHERE state = 'idle' AND now() - state_change > interval '1 hour' AND pid <> pg_backend_pid();","impact":"被终止连接上未提交事务将回滚，须逐一确认归属"}
]$J$::jsonb,
TRUE, TRUE, 20, 'PG 连接使用率/连接数类告警与连接耗尽场景'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_connections');

-- 3.2 PG 事务与锁类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_trx_lock', 'PG 事务与锁类', 'postgresql',
$J$[
  {"matchType":"prefix","pattern":"pg.trx."},
  {"matchType":"exact","pattern":"pg.locks.waiting"},
  {"matchType":"exact","pattern":"pg.blocked_sessions"},
  {"matchType":"exact","pattern":"pg.delta.deadlocks"},
  {"matchType":"exact","pattern":"scenario.pg_long_trx_bloat"},
  {"matchType":"exact","pattern":"scenario.pg_lock_contention"}
]$J$::jsonb,
$J$[
  {"code":"pg.trx.max_seconds","label":"最长事务时长","unit":"s","color":"#E5484D"},
  {"code":"pg.trx.idle_in_trx_max_seconds","label":"事务中空闲最长时长","unit":"s","color":"#E08600"},
  {"code":"pg.locks.waiting","label":"等待中锁请求","unit":"","color":"#9B59B6"},
  {"code":"pg.blocked_sessions","label":"被阻塞会话","unit":"","color":"#6366F1"},
  {"code":"pg.delta.deadlocks","label":"死锁次数/分钟","unit":"","color":"#B91C1C"}
]$J$::jsonb,
$J$[
  {"cause":"应用事务边界缺陷（忘记提交/异常路径漏回滚）","confidence":0.7,"color":"danger","evidence":["当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","事务中空闲时长高是最典型信号：连接开了事务却不动","PG 中该问题额外阻止 VACUUM 清理，引发表膨胀"]},
  {"cause":"长事务持锁阻塞其他会话","confidence":0.65,"color":"warning","evidence":["被阻塞会话数与锁等待同步上升时，通常存在单一阻塞源","用 pg_blocking_pids 找到阻塞链根部的会话"]},
  {"cause":"加锁顺序冲突导致死锁","confidence":0.5,"color":"warning","evidence":["死锁计数增加：PG 已自动回滚一方，业务侧收到 deadlock detected 报错","查数据库日志获取死锁双方的语句与锁信息"]}
]$J$::jsonb,
$J$[
  {"title":"查看事务与锁趋势","description":"在实时概况确认最长事务、事务中空闲、锁等待的起始时间与走势","action":"查看实时概况","link":"pg_realtime"},
  {"title":"定位最老事务与阻塞源","description":"按 xact_start 排序找最老事务，用 pg_blocking_pids 找阻塞链根部","action":"查看知识库","link":"knowledge"},
  {"title":"复盘死锁现场（如有）","description":"数据库日志中有完整的死锁双方语句，据此统一应用加锁顺序","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"定位最老事务","risk":"low","description":"找到钉住 VACUUM 水位的元凶事务","sql":"SELECT pid, usename, state, now()-xact_start AS dur, left(query,100) AS query\nFROM pg_stat_activity WHERE xact_start IS NOT NULL\nORDER BY xact_start LIMIT 10;","impact":"只读查询，无风险"},
  {"action":"查看阻塞链","risk":"low","description":"确认谁在阻塞谁","sql":"SELECT pid, pg_blocking_pids(pid) AS blocked_by, state, left(query,80) AS query\nFROM pg_stat_activity WHERE cardinality(pg_blocking_pids(pid)) > 0;","impact":"只读查询，无风险"},
  {"action":"取消/终止元凶会话（人工确认）","risk":"high","description":"优先 pg_cancel_backend 取消语句；无效再 pg_terminate_backend 断开连接","sql":"-- 取消语句（温和）\nSELECT pg_cancel_backend(<pid>);\n-- 断开连接（事务回滚）\nSELECT pg_terminate_backend(<pid>);","impact":"事务将回滚；须确认业务影响后由 DBA 执行"}
]$J$::jsonb,
TRUE, TRUE, 21, 'PG 长事务/事务中空闲/锁等待/死锁类告警与对应场景'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_trx_lock');

-- 3.3 PG 复制类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_replication', 'PG 复制类', 'postgresql',
$J$[
  {"matchType":"prefix","pattern":"pg.repl."},
  {"matchType":"exact","pattern":"scenario.pg_replication_risk"}
]$J$::jsonb,
$J$[
  {"code":"pg.repl.lag_seconds","label":"复制回放延迟","unit":"s","color":"#E5484D"},
  {"code":"pg.repl.replica_count","label":"下游从库数","unit":"","color":"#0C7C97"},
  {"code":"pg.tps","label":"TPS","unit":"","color":"#15A36A"},
  {"code":"pg.rate.tup_inserted","label":"插入行/秒","unit":"","color":"#E08600"}
]$J$::jsonb,
$J$[
  {"cause":"主库大事务/批量写入超出从库回放能力","confidence":0.65,"color":"warning","evidence":["当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","主库写入速率（插入/更新行速率）峰值时段与延迟上升时段吻合","从库回放为单进程，主库大事务会造成明显回放堆积"]},
  {"cause":"从库长查询与回放冲突","confidence":0.55,"color":"warning","evidence":["从库被当报表库使用时，长查询会暂停回放（max_standby_streaming_delay）","检查从库 pg_stat_activity 是否有长时间运行的查询"]},
  {"cause":"主库空闲导致的假性延迟","confidence":0.4,"color":"info","evidence":["回放延迟按最后回放事务距今估算，主库无写入时该值自然增长","主库 TPS 接近 0 时属正常现象，无需处置"]}
]$J$::jsonb,
$J$[
  {"title":"确认主库写入形态","description":"查看 TPS 与行写入速率，确认延迟是否伴随主库写入峰值","action":"查看实时概况","link":"pg_realtime"},
  {"title":"三段定位延迟瓶颈","description":"在主库 pg_stat_replication 按 send/flush/replay 三段拆解延迟位置","action":"查看知识库","link":"knowledge"},
  {"title":"检查从库回放冲突","description":"确认从库是否有长查询与回放冲突（报表查询迁移或限时）","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"三段拆解复制延迟","risk":"low","description":"在主库执行，确认延迟落在发送/刷盘/回放哪一段","sql":"SELECT application_name, client_addr,\n  pg_wal_lsn_diff(pg_current_wal_lsn(), sent_lsn)  AS send_lag_bytes,\n  pg_wal_lsn_diff(sent_lsn, flush_lsn)             AS flush_lag_bytes,\n  pg_wal_lsn_diff(flush_lsn, replay_lsn)           AS replay_lag_bytes\nFROM pg_stat_replication;","impact":"只读查询，无风险"},
  {"action":"检查从库长查询","risk":"low","description":"在从库执行，确认是否有查询阻碍回放","sql":"SELECT pid, now()-query_start AS dur, left(query,100)\nFROM pg_stat_activity WHERE state='active' ORDER BY query_start LIMIT 10;","impact":"只读查询，无风险"}
]$J$::jsonb,
TRUE, TRUE, 22, 'PG 复制延迟/从库数类告警与复制风险场景'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_replication');

-- 3.4 PG 缓存与吞吐类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_cache_perf', 'PG 缓存与吞吐类', 'postgresql',
$J$[
  {"matchType":"exact","pattern":"pg.cache.hit_rate"},
  {"matchType":"exact","pattern":"pg.delta.temp_files"},
  {"matchType":"prefix","pattern":"pg.rate."},
  {"matchType":"exact","pattern":"pg.tps"}
]$J$::jsonb,
$J$[
  {"code":"pg.cache.hit_rate","label":"缓存命中率","unit":"%","color":"#15A36A"},
  {"code":"pg.delta.temp_files","label":"临时文件数/分钟","unit":"","color":"#6366F1"},
  {"code":"pg.rate.tup_fetched","label":"读取行/秒","unit":"","color":"#0C7C97"},
  {"code":"pg.tps","label":"TPS","unit":"","color":"#E08600"}
]$J$::jsonb,
$J$[
  {"cause":"大表扫描拉低命中率","confidence":0.65,"color":"warning","evidence":["当前值 {value}{unit} 超过阈值 {threshold}{unit}","读取行速率激增而 TPS 平稳时，指向个别语句的大范围扫描","优先补索引消除全表扫描，而不是直接加内存"]},
  {"cause":"shared_buffers 配置偏小","confidence":0.5,"color":"info","evidence":["命中率长期低位且无明显扫描峰值，热数据集可能超出缓存","经验起点为物理内存 25%，同时核对 effective_cache_size"]},
  {"cause":"work_mem 不足导致排序落盘","confidence":0.55,"color":"warning","evidence":["临时文件持续产生：排序/哈希超出 work_mem 落盘","开启 log_temp_files 定位产生临时文件的具体语句"]}
]$J$::jsonb,
$J$[
  {"title":"查看吞吐与缓存趋势","description":"确认命中率下跌/临时文件产生的起始时间，是否与业务变更吻合","action":"查看实时概况","link":"pg_realtime"},
  {"title":"定位扫描大户语句","description":"结合行读取速率与业务侧发布记录定位可疑语句（二期接入 pg_stat_statements 后可直接看 Top SQL）","action":"查看知识库","link":"knowledge"},
  {"title":"评估内存参数","description":"核对 shared_buffers / effective_cache_size / work_mem 配置是否与实例内存匹配","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"查看表级缓存命中","risk":"low","description":"找出物理读集中的表","sql":"SELECT relname, heap_blks_read, heap_blks_hit,\n  round(heap_blks_hit*100.0/nullif(heap_blks_hit+heap_blks_read,0),1) AS hit_pct\nFROM pg_statio_user_tables ORDER BY heap_blks_read DESC LIMIT 10;","impact":"只读查询，无风险"},
  {"action":"开启临时文件日志","risk":"low","description":"记录每个临时文件及产生它的语句","sql":"ALTER SYSTEM SET log_temp_files = 0;\nSELECT pg_reload_conf();","impact":"仅增加日志量，可随时关闭"}
]$J$::jsonb,
TRUE, TRUE, 23, 'PG 缓存命中率/临时文件/吞吐速率类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_cache_perf');

-- 3.5 PG 容量类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_capacity', 'PG 容量类', 'postgresql',
$J$[
  {"matchType":"prefix","pattern":"pg.capacity."}
]$J$::jsonb,
$J$[
  {"code":"pg.capacity.total_size_bytes","label":"实例总容量","unit":"GB","color":"#0C7C97","toGB":true},
  {"code":"pg.capacity.db_size_bytes","label":"监控库大小","unit":"GB","color":"#6366F1","toGB":true},
  {"code":"pg.rate.tup_inserted","label":"插入行/秒","unit":"","color":"#15A36A"}
]$J$::jsonb,
$J$[
  {"cause":"业务数据自然增长","confidence":0.6,"color":"info","evidence":["容量随时间线性增长且与业务量一致，属容量规划问题","按增长趋势预估磁盘耗尽时间，纳入扩容/归档计划"]},
  {"cause":"表膨胀（死元组未回收）","confidence":0.55,"color":"warning","evidence":["容量增速明显高于业务写入量时，膨胀是 PG 最常见的隐性空间大户","检查 pg_stat_user_tables 的 n_dead_tup 与 last_autovacuum"]},
  {"cause":"WAL/临时文件堆积","confidence":0.4,"color":"warning","evidence":["复制槽失活会导致 WAL 无限保留（二期接入复制槽监控）","临时文件大户查询也会造成瞬时空间占用"]}
]$J$::jsonb,
$J$[
  {"title":"查看容量趋势","description":"确认容量增长是持续线性还是突发跳变，突发跳变多为批量导入或膨胀","action":"查看实时概况","link":"pg_realtime"},
  {"title":"排查表膨胀","description":"检查死元组占比 Top 表与 autovacuum 执行情况","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"定位空间大户对象","risk":"low","description":"按总大小排序找 Top 大表（含索引）","sql":"SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) AS total\nFROM pg_stat_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 10;","impact":"只读查询，无风险"},
  {"action":"检查膨胀情况","risk":"low","description":"死元组占比高的表需要 VACUUM 介入","sql":"SELECT relname, n_live_tup, n_dead_tup, last_autovacuum\nFROM pg_stat_user_tables ORDER BY n_dead_tup DESC LIMIT 10;","impact":"只读查询，无风险"},
  {"action":"低峰期回收膨胀空间","risk":"medium","description":"对膨胀表执行 VACUUM；需归还操作系统空间时评估 pg_repack","sql":"VACUUM (VERBOSE, ANALYZE) <表名>;","impact":"普通 VACUUM 不锁表但消耗 IO，建议低峰执行；VACUUM FULL 锁表禁止随意使用"}
]$J$::jsonb,
TRUE, TRUE, 24, 'PG 库容量类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_capacity');

-- 3.6 PG 通用类（prefix "pg." 最短前缀，仅在无更具体画像时兜底；覆盖可用性/基线等）
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'pg_generic', 'PG 通用类', 'postgresql',
$J$[
  {"matchType":"prefix","pattern":"pg."},
  {"matchType":"prefix","pattern":"scenario.pg_"}
]$J$::jsonb,
$J$[
  {"code":"pg.availability","label":"可用性（0/1）","unit":"","color":"#E5484D"},
  {"code":"pg.conn.total","label":"当前连接数","unit":"","color":"#0C7C97"},
  {"code":"pg.tps","label":"TPS","unit":"","color":"#15A36A"},
  {"code":"pg.cache.hit_rate","label":"缓存命中率","unit":"%","color":"#6366F1"}
]$J$::jsonb,
$J$[
  {"cause":"实例或网络不可用","confidence":0.6,"color":"danger","evidence":["可用性=0 表示监控连接失败：检查 postgres 进程、监听端口与网络链路","伴随全部指标断档时优先确认主机存活"]},
  {"cause":"指标异常偏离基线","confidence":0.5,"color":"warning","evidence":["对照趋势图确认异常起始时间，与发布/变更/批处理时间窗比对","基线类告警表示当前值显著偏离过去 7 天同时段水平"]}
]$J$::jsonb,
$J$[
  {"title":"查看实时概况","description":"从连接、事务、吞吐、复制、容量五个板块整体确认异常范围","action":"查看实时概况","link":"pg_realtime"},
  {"title":"查看采集日志","description":"可用性类告警先确认采集链路：连接失败的具体报错在采集日志中","action":"查看采集任务","link":"collector"},
  {"title":"查阅知识库","description":"按告警类型查阅对应的 PG 排查文章","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"验证数据库连通性","risk":"low","description":"在监控服务器上手动验证连接","sql":"psql -h <主机> -p <端口> -U <监控账号> -d postgres -c 'SELECT version();'","impact":"只读验证，无风险"},
  {"action":"检查 postgres 进程与端口","risk":"low","description":"确认实例进程存活与端口监听","sql":"-- Linux\nsystemctl status postgresql\nss -lntp | grep 5432","impact":"只读查看，无风险"}
]$J$::jsonb,
TRUE, TRUE, 29, 'PG 可用性/基线/未细分指标兜底画像'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'pg_generic');

-- ---- 4. PG 分组「场景管理」菜单（复用通用场景页，包装组件挂载） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
SELECT '场景管理', 'pg_scenario', 'menu', '实例级', m.icon, 'scenario', 'monitor/pg/scenario',
       m.perm, 3, 'enabled', TRUE,
       (SELECT id FROM sys_menu WHERE code = 'monitor_pg'),
       'PostgreSQL 场景管理（与 MySQL 共用通用场景页，场景按实例类型自动过滤）', m.buttons
  FROM sys_menu m
 WHERE m.code = 'scenario_mgmt'
ON CONFLICT (code) DO NOTHING;

-- ---- 5. 场景关联知识库文章（按标题回查 id） ----
UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 连接耗尽与连接池配置', 'PostgreSQL 事务中空闲（idle in transaction）连接治理'))
 WHERE s.scenario_code = 'scenario.pg_connection_exhaustion';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 长事务与表膨胀排查指南', 'PostgreSQL 事务中空闲（idle in transaction）连接治理'))
 WHERE s.scenario_code = 'scenario.pg_long_trx_bloat';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 死锁定位与预防', 'PostgreSQL 长事务与表膨胀排查指南'))
 WHERE s.scenario_code = 'scenario.pg_lock_contention';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('PostgreSQL 流复制延迟排查方法'))
 WHERE s.scenario_code = 'scenario.pg_replication_risk';
