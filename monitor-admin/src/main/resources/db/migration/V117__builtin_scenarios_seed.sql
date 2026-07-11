-- =============================================================
-- V117：首批内置场景种子 + 配套知识库文章（需求 §11.8 首批场景，全脚本幂等）
--
-- 首批 4 个场景（磁盘/OS 级指标当前未采集，「磁盘空间预警」「Buffer Pool 压力」暂缓）：
--   1. 连接池耗尽风险（AND）：连接使用率高 + 活跃线程高 + QPS 未同步上涨 → 连接泄漏/阻塞
--   2. SQL 性能劣化（AND）  ：慢 SQL 环比激增 + 语句平均延迟高 → 执行计划变化/索引失效
--   3. 锁竞争与长事务（OR） ：锁等待 / 锁超时 / 长事务任一命中 → 立即排查阻塞链路
--   4. 主从复制风险（AND）  ：复制线程运行中 + 延迟持续升高 → 回放能力不足/大事务
--
-- 条件组树 schema（可嵌套，首批全部单层）：
--   {"logic":"AND|OR","duration":秒,
--    "children":[{"type":"condition","code":"...","name":"...","metricCode":"...",
--                 "condType":"threshold|rate_change","operator":">=","threshold":85,
--                 "unit":"%","compareOffset":"1h","exprText":"≥ 85%"}]}
--   condType=rate_change 时：当前值相对 compareOffset 前取值的变化百分比参与比较
-- =============================================================

-- ---- 1. 配套知识库文章（按标题幂等） ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES
  ('连接池耗尽排查手册', 'fault', '["MySQL","连接","故障诊断"]',
   '<h2>典型特征</h2><p>连接使用率与活跃线程数持续升高，但 QPS 未同步上涨，说明连接被占用而未有效工作。</p><h2>排查步骤</h2><ol><li>执行 <code>SHOW PROCESSLIST</code>，统计 Sleep 状态连接的来源主机与用户，定位未释放连接的应用。</li><li>检查是否存在长时间 Query 状态的慢 SQL 阻塞连接释放。</li><li>核对应用连接池配置（最大连接数、空闲回收时间）与 <code>wait_timeout</code> 是否匹配。</li></ol><h2>处置建议</h2><p>优先在应用侧修复连接泄漏；紧急情况下可临时调大 <code>max_connections</code> 缓解，但需人工确认。</p>'),
  ('如何定位应用连接泄漏', 'fault', '["MySQL","连接","连接泄漏"]',
   '<h2>判断依据</h2><p>连接数随时间单调增长且不随业务量回落，是连接泄漏的典型信号。</p><h2>定位方法</h2><ol><li>按 <code>information_schema.processlist</code> 的 host/user 分组计数，找出连接数异常的来源。</li><li>结合应用连接池监控（active/idle 数量）确认借出未归还的位置。</li><li>审查代码中未关闭 Connection/Statement/ResultSet 的路径，尤其是异常分支。</li></ol>'),
  ('慢 SQL 导致连接堆积的处理方法', 'performance', '["MySQL","慢查询","连接"]',
   '<h2>机制说明</h2><p>慢 SQL 长时间占用连接，后续请求排队新建连接，最终逼近 <code>max_connections</code> 上限。</p><h2>处理步骤</h2><ol><li>通过慢查询日志或 Top SQL 定位当前耗时最高的语句。</li><li>用 <code>EXPLAIN</code> 检查执行计划，确认是否全表扫描或索引失效。</li><li>评估后由人工在业务低峰终止阻塞会话，并推动 SQL 优化。</li></ol>'),
  ('Top SQL 分析方法', 'performance', '["MySQL","Top SQL","性能优化"]',
   '<h2>数据来源</h2><p>MySQL 5.7+ 可使用 <code>performance_schema.events_statements_summary_by_digest</code> 按语句指纹聚合耗时、扫描行数与执行次数。</p><h2>分析路径</h2><ol><li>按总耗时排序找出资源消耗最大的语句。</li><li>对比执行次数与平均耗时，区分「高频小查询」与「低频大查询」。</li><li>关注 rows_examined 与 rows_sent 比值，识别低效扫描。</li></ol>'),
  ('执行计划突变排查指南', 'performance', '["MySQL","执行计划","慢查询"]',
   '<h2>常见诱因</h2><ol><li>统计信息陈旧导致优化器选错索引，可执行 <code>ANALYZE TABLE</code> 刷新。</li><li>近期发布的 SQL 变更（新增条件、隐式类型转换）使原索引失效。</li><li>数据量增长跨过优化器成本拐点。</li></ol><h2>确认方法</h2><p>对慢语句执行 <code>EXPLAIN</code>，与历史执行计划比对 type/key/rows 字段的变化。</p>'),
  ('InnoDB 死锁分析与预防', 'fault', '["MySQL","InnoDB","死锁"]',
   '<h2>获取现场</h2><p>执行 <code>SHOW ENGINE INNODB STATUS</code> 查看 LATEST DETECTED DEADLOCK 区块，或开启 <code>innodb_print_all_deadlocks</code> 记录全部死锁。</p><h2>预防措施</h2><ol><li>事务内按固定顺序访问表和行。</li><li>缩短事务持锁时间，避免事务内交互等待。</li><li>为检索条件建立合适索引，减少锁定范围。</li></ol>'),
  ('如何查看当前锁等待链路', 'fault', '["MySQL","锁等待","故障诊断"]',
   '<h2>按版本取数</h2><ul><li>5.6：<code>information_schema.innodb_lock_waits</code> 关联 <code>innodb_trx</code>。</li><li>5.7：<code>sys.innodb_lock_waits</code> 直接给出阻塞与等待会话。</li><li>8.0：<code>performance_schema.data_lock_waits</code>。</li></ul><h2>处置</h2><p>定位阻塞源头事务后，评估其业务影响，由人工确认是否终止会话。</p>'),
  ('长事务的危害与治理', 'practice', '["MySQL","长事务","事务"]',
   '<h2>危害</h2><ul><li>undo 日志无法清理，表空间膨胀。</li><li>MVCC 读视图变旧，查询代价放大。</li><li>持锁时间长，加剧锁等待与死锁。</li></ul><h2>治理</h2><ol><li>通过 <code>information_schema.innodb_trx</code> 监控 <code>trx_started</code> 超过阈值的事务。</li><li>推动应用拆分大事务、避免事务内远程调用。</li><li>为批量任务设置合理的提交批次。</li></ol>'),
  ('从库回放慢的常见原因', 'fault', '["MySQL","复制","主从延迟"]',
   '<h2>常见原因</h2><ol><li>从库单线程回放跟不上主库并发写入（5.7+ 可开启并行复制 <code>slave_parallel_workers</code>）。</li><li>主库大事务（如批量 UPDATE）在从库串行回放耗时放大。</li><li>从库磁盘 IO 或 CPU 能力弱于主库。</li><li>从库承担大量读流量与回放争抢资源。</li></ol><h2>确认方法</h2><p>对比 relay log 读取位点与回放位点的推进速度，判断瓶颈在拉取还是回放环节。</p>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 2. 内置场景 ----

-- [1] 连接池耗尽风险（严重，AND）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.connection_pool_exhaustion',
    '连接池耗尽风险',
    '综合判断连接使用率、活跃线程、QPS 三个信号，区分「业务流量正常增长」和「连接泄漏/阻塞」两种根因，避免单一阈值误报',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":120,"children":[
       {"type":"condition","code":"conn_usage","name":"连接使用率","metricCode":"mysql.conn.usage",
        "condType":"threshold","operator":">=","threshold":85,"unit":"%","exprText":"≥ 85%"},
       {"type":"condition","code":"threads_running","name":"活跃线程数","metricCode":"mysql.status.Threads_running",
        "condType":"threshold","operator":">=","threshold":50,"unit":"count","exprText":"≥ 50"},
       {"type":"condition","code":"qps_flat","name":"QPS 环比","metricCode":"mysql.qps",
        "condType":"rate_change","compareOffset":"1h","operator":"<","threshold":20,"unit":"%",
        "exprText":"较1小时前上涨 < 20%（无显著上涨）"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '连接被大量占用但 QPS 未同步上涨，典型连接泄漏或慢 SQL 阻塞特征，建议排查应用连接池与长时间未提交的事务',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [2] SQL 性能劣化（预警，AND）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.sql_performance_degradation',
    'SQL 性能劣化',
    '结合慢 SQL 环比增量与语句平均延迟联合判断，区分「SQL 变更引起的计划变化」和「偶发波动」，比单一慢 SQL 阈值更准确',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":120,"children":[
       {"type":"condition","code":"slow_surge","name":"慢 SQL 增量环比","metricCode":"mysql.delta.slow_queries",
        "condType":"rate_change","compareOffset":"1h","operator":">=","threshold":50,"unit":"%",
        "exprText":"较1小时前增加 ≥ 50%"},
       {"type":"condition","code":"latency_high","name":"语句平均延迟","metricCode":"mysql.perf.avg_stmt_latency_ms",
        "condType":"threshold","operator":">=","threshold":500,"unit":"ms","exprText":"≥ 500ms"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '慢 SQL 急剧增多且语句平均延迟明显上升，指向索引失效、执行计划变化或近期 SQL 发布变更，建议对比 Top SQL 与执行计划',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [3] 锁竞争与长事务（严重，OR）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.lock_contention',
    '锁竞争与长事务',
    '锁等待、锁超时、长事务三个信号联合监控（任一命中即触发），区分偶发锁冲突和系统性锁竞争，并提示排查阻塞源',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"OR","duration":0,"children":[
       {"type":"condition","code":"lock_waits","name":"锁等待数","metricCode":"mysql.innodb.lock_waits",
        "condType":"threshold","operator":">=","threshold":5,"unit":"count","exprText":"≥ 5"},
       {"type":"condition","code":"lock_timeout","name":"锁等待超时次数","metricCode":"mysql.innodb.lock_timeout_count",
        "condType":"threshold","operator":">=","threshold":1,"unit":"count","exprText":"> 0（本采集周期）"},
       {"type":"condition","code":"long_trx","name":"最长活跃事务","metricCode":"mysql.innodb.trx_max_seconds",
        "condType":"threshold","operator":">=","threshold":60,"unit":"s","exprText":"≥ 60s"}
     ]}'::jsonb,
    '{"duration":180}'::jsonb,
    '检测到锁竞争或长事务信号，建议立即通过锁等待链路定位阻塞源事务，并评估是否需要人工终止会话',
    '[{"when":["lock_timeout"],"text":"出现行锁等待超时，事务已因等待锁失败，建议排查热点行竞争与事务持锁时长"},
      {"when":["long_trx"],"text":"存在长时间未提交的活跃事务，可能造成 undo 积压并阻塞其他会话，建议定位事务来源并推动提交或回滚"}]'::jsonb,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [4] 主从复制风险（严重，AND）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.replication_risk',
    '主从复制风险',
    '复制延迟结合 IO/SQL 线程状态综合判断：线程正常但延迟持续升高，指向从库回放能力不足或主库大事务，而非复制链路故障（线程停止已由内置规则覆盖）',
    'level_2',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":120,"children":[
       {"type":"condition","code":"repl_delay","name":"复制延迟","metricCode":"mysql.replication.seconds_behind",
        "condType":"threshold","operator":">=","threshold":30,"unit":"s","exprText":"≥ 30s"},
       {"type":"condition","code":"io_running","name":"IO 线程状态","metricCode":"mysql.replication.io_running",
        "condType":"threshold","operator":">=","threshold":1,"unit":"","exprText":"= Running"},
       {"type":"condition","code":"sql_running","name":"SQL 线程状态","metricCode":"mysql.replication.sql_running",
        "condType":"threshold","operator":">=","threshold":1,"unit":"","exprText":"= Running"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '复制线程正常运行但延迟持续升高，指向从库回放能力不足或主库写入峰值（大事务/批量写入），建议检查主库 binlog 写入量与从库并行复制配置',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 3. 场景关联知识库文章（按标题回查 id） ----
UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('连接池耗尽排查手册', '如何定位应用连接泄漏', '慢 SQL 导致连接堆积的处理方法'))
 WHERE s.scenario_code = 'scenario.connection_pool_exhaustion';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('Top SQL 分析方法', '执行计划突变排查指南', 'MySQL 慢查询优化指南'))
 WHERE s.scenario_code = 'scenario.sql_performance_degradation';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('InnoDB 死锁分析与预防', '如何查看当前锁等待链路', '长事务的危害与治理'))
 WHERE s.scenario_code = 'scenario.lock_contention';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('主从复制延迟排查方法', '从库回放慢的常见原因'))
 WHERE s.scenario_code = 'scenario.replication_risk';
