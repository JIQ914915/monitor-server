-- =============================================================
-- V129：知识库内容扩充（对照系统已实现功能补齐配套文章）
--
-- 补齐目标（按功能模块）：
--   1. 内置告警规则（V114）：实例重启、连接失败/连接被拒 → 排查文章
--   2. 告警下钻画像（V127）多处「查看知识库」引导落点：
--        连接池配置匹配、并行复制配置、归档与扩容、监控账号权限
--   3. 健康评分（V125 五维）：评分体系说明与提升建议
--   4. 告警事件处理（V97 状态流转：确认/受理/静默/关闭）：处理流程规范
--   5. 采集能力（Performance Schema / sys schema / 慢查询日志）：配置入门
--   6. 性能分析页（QPS/TPS/延迟）：核心吞吐指标解读
--
-- 全脚本幂等：文章按标题防重插入；场景关联按 id 存在性守卫追加。
-- =============================================================

-- ---- 1. 新增知识文章 ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES

  -- 1.1 实例意外重启（配套 builtin.instance.restarted）
  ('MySQL 实例意外重启排查指南', 'fault', '["MySQL","可用性","故障诊断"]',
   '<h2>如何确认发生了重启</h2><p>监控平台基于 <code>SHOW GLOBAL STATUS</code> 中 <code>Uptime</code> 的下降判定重启：本轮运行时长小于上轮，说明两次采集之间实例重启过。收到「实例发生重启」告警后按以下顺序排查。</p><h2>排查步骤</h2><ol><li><strong>确认是否计划内操作</strong>：核对变更记录，排除人工重启、参数变更重启、发布窗口操作。</li><li><strong>查看 MySQL 错误日志</strong>：定位 <code>error log</code> 中最后一次启动前的日志，关注 <code>Signal 11</code>（崩溃）、断言失败、InnoDB 损坏等记录。</li><li><strong>检查是否被 OOM 终止</strong>：执行 <code>dmesg | grep -i oom</code> 或查看 <code>/var/log/messages</code>，确认内核是否因内存不足杀掉 mysqld。常见诱因是 <code>innodb_buffer_pool_size</code> 与连接内存（<code>sort_buffer_size</code> 等 × 连接数）之和超出物理内存。</li><li><strong>检查主机与虚拟化层</strong>：主机重启、虚拟机迁移、存储故障都会表现为实例重启。</li></ol><h2>重启后的健康确认</h2><ul><li>确认 InnoDB 崩溃恢复完成（错误日志出现 ready for connections）。</li><li>Buffer Pool 冷启动后命中率需要时间爬升，短时间性能下降属预期，可关注缓存命中率指标恢复情况。</li><li>主从架构中若从库重启，检查复制线程是否自动恢复运行。</li></ul>'),

  -- 1.2 连接失败与连接被拒（配套 builtin.aborted_connects.warning / builtin.conn.rejected.critical）
  ('连接失败与连接被拒的排查方法', 'fault', '["MySQL","连接","故障诊断"]',
   '<h2>两类问题的区分</h2><ul><li><strong>连接失败（Aborted_connects）</strong>：客户端发起连接但握手失败——密码错误、账号权限不足、握手超时、网络中断。</li><li><strong>连接被拒（Connection_errors_max_connections）</strong>：连接数达到 <code>max_connections</code> 上限，服务端直接拒绝，业务请求正在失败，属更紧急情况。</li></ul><h2>连接失败排查</h2><ol><li>核对最近是否有应用改密、账号变更未同步到所有实例。</li><li>检查 <code>performance_schema.host_cache</code> 中的错误统计（8.0 可用 <code>sys.host_summary</code>），按来源主机定位失败集中点。</li><li>失败来源为陌生 IP 且持续存在时，警惕口令扫描，建议收紧网络访问策略。</li><li>偶发失败伴随网络抖动时，检查 <code>connect_timeout</code> 与网络质量。</li></ol><h2>连接被拒处置</h2><ol><li>立即执行 <code>SHOW PROCESSLIST</code> 排查连接占用：大量 Sleep 连接指向应用连接池泄漏或空闲回收配置过长。</li><li>确认是否有慢 SQL 阻塞导致连接不释放（结合慢 SQL 分析页定位）。</li><li>确属业务量增长时，评估调大 <code>max_connections</code>，同时同步调整应用连接池上限，避免单纯放大上限掩盖泄漏问题。</li></ol>'),

  -- 1.3 连接池配置匹配（下钻画像 connections「评估连接池配置」落点）
  ('应用连接池与 max_connections 配置匹配指南', 'practice', '["MySQL","连接","最佳实践"]',
   '<h2>基本原则</h2><p>数据库 <code>max_connections</code> 必须大于所有应用实例连接池上限之和，并留出运维与监控余量（建议 10%～20%）。</p><p>示例：4 个应用节点 × 连接池最大 50 = 200，则 <code>max_connections</code> 建议不低于 240。</p><h2>连接池关键参数</h2><ul><li><strong>最大连接数（maxActive/maximumPoolSize）</strong>：按压测得出的并发需求设置，不是越大越好——数据库侧活跃线程超过 CPU 核数数倍后吞吐反而下降。</li><li><strong>最小空闲（minIdle）</strong>：保持少量热连接，避免突发流量时集中建连。</li><li><strong>空闲回收（idleTimeout）</strong>：应小于 MySQL 的 <code>wait_timeout</code>，否则连接池会借出已被服务端关闭的失效连接。</li><li><strong>借出检测（testOnBorrow / validationQuery）</strong>：网络不稳定环境建议开启，或使用更轻量的 keepalive 探测。</li></ul><h2>常见反模式</h2><ol><li>连接池上限设置过大（如单节点 500+），数据库线程调度开销激增。</li><li>多个应用共用同一账号，出现问题无法按来源定位（建议按应用分配账号）。</li><li>只调大 <code>max_connections</code> 不排查泄漏，连接继续单调增长直至再次打满。</li></ol>'),

  -- 1.4 监控账号权限（下钻画像 collect_fail「检查监控账号权限」落点）
  ('监控采集账号权限配置指南', 'practice', '["MySQL","运维","采集"]',
   '<h2>最小权限原则</h2><p>监控账号只需读取状态与系统视图，不需要业务数据的读写权限。推荐授权：</p><pre><code>CREATE USER ''monitor''@''%'' IDENTIFIED BY ''******'';
GRANT PROCESS, REPLICATION CLIENT ON *.* TO ''monitor''@''%'';
GRANT SELECT ON performance_schema.* TO ''monitor''@''%'';
GRANT SELECT ON information_schema.* TO ''monitor''@''%'';</code></pre><ul><li><strong>PROCESS</strong>：查看全部会话（PROCESSLIST）、InnoDB 状态。</li><li><strong>REPLICATION CLIENT</strong>：执行 <code>SHOW REPLICA STATUS</code> 采集复制指标。</li><li><strong>performance_schema 只读</strong>：Top SQL、等待事件、语句延迟等指标来源。</li></ul><h2>常见采集失败原因</h2><ol><li>账号密码错误或过期（8.0 默认密码过期策略）——采集日志表现为认证失败。</li><li>缺少 PROCESS 权限——连接信息、长连接列表采集为空。</li><li>缺少 REPLICATION CLIENT——复制延迟指标缺失。</li><li>账号 host 限制与采集器部署位置不匹配（如只授权了 localhost）。</li><li>performance_schema 未开启（<code>performance_schema=OFF</code>）——慢 SQL 指纹与语句统计无数据。</li></ol><h2>验证方法</h2><p>用监控账号手动登录目标实例，依次执行 <code>SHOW GLOBAL STATUS</code>、<code>SHOW PROCESSLIST</code>、<code>SHOW REPLICA STATUS</code> 与 performance_schema 查询，确认均可正常返回。</p>'),

  -- 1.5 健康评分体系（配套 V125 五维健康得分）
  ('健康评分五维体系说明与提升建议', 'practice', '["监控","健康评分","运维"]',
   '<h2>五维构成</h2><p>实例健康总分由五个维度加权得出，各维度按对应指标的达标情况打分（0～100，-1 表示该维度暂无数据）：</p><ul><li><strong>可用性</strong>：实例存活、连接可用性、实例重启情况。</li><li><strong>性能</strong>：语句平均延迟、慢 SQL 数量、QPS/TPS 波动。</li><li><strong>稳定性</strong>：锁等待、死锁、长事务、复制延迟等风险信号。</li><li><strong>容量</strong>：表空间与 Binlog 占用增长趋势、连接使用率。</li><li><strong>安全配置</strong>：账号策略、关键参数配置合规情况。</li></ul><h2>看分思路</h2><ol><li>总分下降先看弹窗中的<strong>扣分明细</strong>，每条扣分项都标注了来源维度与原因。</li><li>单维度长期偏低说明存在系统性问题（如容量维度低 = 增长趋势不健康），应制定专项治理计划，而非只处理单次告警。</li><li>多实例对比时，关注离群实例：同业务的实例分数明显低于同组，优先排查配置差异。</li></ol><h2>提升建议</h2><ul><li>性能维度：结合慢 SQL 分析页处理 Top 指纹，标记优化状态形成闭环。</li><li>稳定性维度：治理长事务与锁竞争（参见《长事务的危害与治理》）。</li><li>容量维度：建立归档机制，参见《表空间容量增长分析与归档策略》。</li></ul>'),

  -- 1.6 容量增长与归档（下钻画像 capacity「评估归档与扩容计划」落点）
  ('表空间容量增长分析与归档策略', 'practice', '["MySQL","容量","运维"]',
   '<h2>增长分析方法</h2><ol><li>通过容量趋势图确认增长形态：<strong>线性增长</strong>属业务自然积累，按增速预估耗尽时间；<strong>陡增</strong>需定位事件（批量导入、日志表失控、大事务写入放大）。</li><li>按表定位增长来源：<code>information_schema.TABLES</code> 中 <code>data_length + index_length</code> 排序，找出 Top 增长表。</li><li>区分数据增长与碎片膨胀：大量 DELETE 后空间不会自动归还，<code>data_free</code> 偏大时属碎片。</li></ol><h2>归档策略</h2><ul><li><strong>时间序列类大表</strong>（日志、流水、明细）：按时间分区或分表，过期分区直接 DROP，成本远低于 DELETE。</li><li><strong>历史数据仍需查询</strong>：迁移至归档库/冷存储，线上表只保留热数据窗口（如 3～6 个月）。</li><li><strong>DELETE 归档后回收空间</strong>：业务低峰执行 <code>OPTIMIZE TABLE</code> 或 <code>ALTER TABLE ... ENGINE=InnoDB</code> 重建（注意锁与磁盘临时空间）。</li></ul><h2>扩容评估</h2><p>归档无法覆盖增长时再考虑扩容：预估未来 6～12 个月数据量，磁盘使用率长期控制在 70% 以内，为突发写入与备份留出空间。</p>'),

  -- 1.7 并行复制配置（下钻画像 replication「评估并行复制」落点）
  ('MySQL 并行复制配置实践（5.6/5.7/8.0）', 'mysql', '["MySQL","复制","主从延迟"]',
   '<h2>各版本能力差异</h2><ul><li><strong>5.6</strong>：仅支持按库（schema）并行（<code>slave_parallel_workers</code>），单库业务无收益。</li><li><strong>5.7</strong>：引入 <code>slave_parallel_type = LOGICAL_CLOCK</code>，基于组提交并行，单库也能并行回放，是 5.7 从库延迟治理的关键手段。</li><li><strong>8.0</strong>：默认基于 WRITESET 的依赖跟踪（8.0.27 起 <code>replica_parallel_workers</code> 默认 4），并行度更高。</li></ul><h2>5.7 推荐配置</h2><pre><code>slave_parallel_type = LOGICAL_CLOCK
slave_parallel_workers = 4        -- 按从库 CPU 核数调整（4~8 常见）
slave_preserve_commit_order = ON  -- 保证提交顺序与主库一致
-- 主库配合（提高组提交并发度）：
binlog_group_commit_sync_delay = 10000   -- 微秒，按业务容忍度权衡</code></pre><h2>8.0 推荐配置</h2><pre><code>binlog_transaction_dependency_tracking = WRITESET   -- 主库（8.0.26 前）
replica_parallel_type = LOGICAL_CLOCK
replica_parallel_workers = 8
replica_preserve_commit_order = ON</code></pre><h2>注意事项</h2><ol><li>并行复制解决的是<strong>回放吞吐</strong>问题；若延迟来自大事务，应优先拆分事务（参见《大事务对复制与磁盘的影响》）。</li><li>调整 worker 数需重启复制线程：<code>STOP REPLICA; SET GLOBAL ...; START REPLICA;</code>。</li><li>观察调整效果：对比调整前后复制延迟趋势曲线即可量化收益。</li></ol>'),

  -- 1.8 告警事件处理流程（配套 V97 事件状态流转）
  ('告警事件处理流程与最佳实践', 'practice', '["监控","告警","运维"]',
   '<h2>事件状态流转</h2><p>平台告警事件遵循「触发 → 确认 → 受理 → 关闭」的处理链路，另可对已知噪音事件执行静默：</p><ul><li><strong>确认</strong>：值班人员看到告警后第一时间确认，表示「有人在跟进」，避免多人重复响应。</li><li><strong>受理</strong>：开始实质排查处置，事件进入处理中状态。</li><li><strong>关闭</strong>：根因消除或指标恢复后关闭，并在备注中记录处理结论。</li><li><strong>静默</strong>：确认为已知问题（如计划内维护窗口）时静默，到期自动恢复告警能力。</li></ul><h2>处理最佳实践</h2><ol><li><strong>分级响应</strong>：一二级告警要求即时响应；三四级可合并到日巡检处理。</li><li><strong>善用下钻</strong>：从事件详情进入下钻页，按画像给出的关联指标、可能原因和排查路径逐步定位，避免凭经验乱查。</li><li><strong>留痕闭环</strong>：每次状态流转填写备注，操作记录自动留存，便于复盘与交接。</li><li><strong>输出报告</strong>：重要事件处理完成后在下钻页导出事件处理报告，作为上报与归档材料。</li></ol><h2>降噪治理</h2><p>同一规则反复触发又自动恢复，说明阈值贴近业务正常波动，应调整阈值或持续时间条件，而不是长期静默——静默只是临时手段。</p>'),

  -- 1.9 Performance Schema 入门（8.0 采集能力依赖）
  ('Performance Schema 与 sys schema 使用入门', 'mysql', '["MySQL","Performance Schema","性能优化"]',
   '<h2>作用与开销</h2><p>Performance Schema（PS）是 MySQL 内置的性能数据引擎，语句统计、等待事件、锁信息均来源于此；sys schema（5.7+ 内置）是在 PS 之上的一层易读视图。默认配置的开销通常在 1%～5%，生产环境建议保持开启（<code>performance_schema = ON</code>）。</p><h2>监控平台依赖的核心表</h2><ul><li><code>events_statements_summary_by_digest</code>：按 SQL 指纹聚合的执行统计——Top SQL、慢 SQL 指纹分析的数据来源。</li><li><code>data_lock_waits</code>（8.0）/ <code>sys.innodb_lock_waits</code>（5.7）：锁等待链路。</li><li><code>host_cache</code>：连接失败统计。</li></ul><h2>常用 sys 视图速查</h2><pre><code>SELECT * FROM sys.statements_with_full_table_scans;  -- 全表扫描语句
SELECT * FROM sys.schema_unused_indexes;             -- 未使用的索引
SELECT * FROM sys.io_global_by_file_by_bytes LIMIT 10; -- IO 热点文件
SELECT * FROM sys.memory_global_by_current_bytes LIMIT 10; -- 内存占用
</code></pre><h2>版本注意事项</h2><ol><li>5.6 的 PS 功能有限（无 digest 汇总的部分列），平台会自动降级采集范围。</li><li>digest 表有容量上限（<code>performance_schema_digests_size</code>，默认约 1 万条指纹），超限后新指纹归入 NULL 行，指纹极多的实例可适当调大。</li><li><code>TRUNCATE performance_schema.events_statements_summary_by_digest</code> 可重置统计，重置后累计类指标从零开始。</li></ol>'),

  -- 1.10 慢查询日志配置（慢 SQL 模块采集前提）
  ('慢查询日志配置详解', 'mysql', '["MySQL","慢查询","配置"]',
   '<h2>核心参数</h2><pre><code>slow_query_log = ON                 -- 开启慢查询日志
long_query_time = 1                 -- 阈值（秒），支持小数如 0.5
log_queries_not_using_indexes = OFF -- 记录未走索引的查询（噪音大，按需开启）
slow_query_log_file = slow.log      -- 日志文件位置
log_slow_admin_statements = ON      -- 记录 ALTER/ANALYZE 等管理语句（5.7+）
log_slow_replica_statements = ON    -- 记录从库回放中的慢语句（8.0.26+，旧版本为 log_slow_slave_statements）</code></pre><h2>阈值设定建议</h2><ul><li>OLTP 业务建议 0.5～1 秒起步，过低会产生海量日志。</li><li>阈值可在线修改：<code>SET GLOBAL long_query_time = 0.5;</code>（新连接生效）。</li><li>平台的慢 SQL 指纹分析基于 performance_schema 语句统计，与慢日志互补：慢日志保留完整现场（含参数），指纹统计覆盖全量语句聚合趋势。</li></ul><h2>日志治理</h2><ol><li>慢日志文件持续增长需纳入轮转（<code>FLUSH SLOW LOGS</code> 配合 logrotate）。</li><li>集中分析可用 <code>pt-query-digest</code> 按指纹聚合，输出与平台 Top SQL 类似的报表用于交叉验证。</li></ol>'),

  -- 1.11 吞吐指标解读（性能分析页配套）
  ('QPS、TPS 与语句延迟指标解读', 'performance', '["MySQL","监控","性能优化"]',
   '<h2>指标含义</h2><ul><li><strong>QPS</strong>：每秒查询数，来自 Questions/Queries 计数器增量，反映整体请求量。</li><li><strong>TPS</strong>：每秒事务数，来自 Com_commit + Com_rollback 增量，反映写入与事务负载。</li><li><strong>语句平均延迟</strong>：performance_schema 中语句耗时的平均值，是比 QPS 更直接的「用户体感」指标。</li></ul><h2>组合解读方法</h2><ol><li><strong>QPS 高 + 延迟低</strong>：健康的高负载，关注容量余量即可。</li><li><strong>QPS 平稳 + 延迟上升</strong>：性能劣化信号——执行计划退化、锁竞争或缓存命中率下降，结合慢 SQL 与锁指标下钻。</li><li><strong>QPS 骤降 + 连接堆积</strong>：请求被阻塞（锁、慢 SQL 占满连接），属故障前兆，立即排查阻塞链路。</li><li><strong>TPS 陡增 + Binlog 占用陡增</strong>：批量写入或写入放大，关注复制延迟与磁盘容量联动变化。</li></ol><h2>基线思维</h2><p>吞吐指标没有统一的「好坏阈值」，应与自身历史基线对比：平台趋势图支持环比查看，工作日同时段对比最具参考性。突破基线 ±50% 且无对应业务事件时，值得下钻排查。</p>'),

  -- 1.12 版本差异总览（平台支持 5.6/5.7/8.0 的配套说明）
  ('MySQL 5.6/5.7/8.0 监控能力差异速查', 'mysql', '["MySQL","版本差异","监控"]',
   '<h2>为什么关注版本差异</h2><p>平台对 MySQL 5.6、5.7、8.0 的采集能力随版本原生特性递增，部分指标与场景仅在高版本可用。了解差异有助于解读「为什么这个实例没有某项数据」。</p><h2>关键差异速查</h2><table border="1" cellspacing="0" cellpadding="6" style="border-collapse:collapse"><tr><th>能力</th><th>5.6</th><th>5.7</th><th>8.0</th></tr><tr><td>SQL 指纹统计（digest）</td><td>基础</td><td>完整</td><td>完整（含直方图）</td></tr><tr><td>sys schema</td><td>需手动安装</td><td>内置</td><td>内置</td></tr><tr><td>锁等待视图</td><td>information_schema.innodb_lock_waits</td><td>sys.innodb_lock_waits</td><td>performance_schema.data_lock_waits</td></tr><tr><td>并行复制</td><td>按库</td><td>LOGICAL_CLOCK</td><td>WRITESET（默认更优）</td></tr><tr><td>复制状态语句</td><td>SHOW SLAVE STATUS</td><td>SHOW SLAVE STATUS</td><td>SHOW REPLICA STATUS</td></tr><tr><td>角色/账号管理</td><td>基础</td><td>基础</td><td>角色、密码策略、双密码</td></tr></table><h2>升级建议</h2><ol><li>5.6 已停止官方支持，锁与语句级观测能力有限，建议规划升级。</li><li>5.7 → 8.0 升级前重点验证：保留字冲突、默认字符集（utf8mb4）、GROUP BY 隐式排序移除、认证插件（caching_sha2_password）与旧客户端兼容性。</li><li>升级后复制拓扑建议从库先行，观察一个完整业务周期再切主。</li></ol>')

) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 2. 内置场景关联新文章（存在性守卫，幂等追加） ----

-- 连接池耗尽风险 += 连接池配置匹配、连接失败排查
UPDATE monitor_scenario s
   SET knowledge_article_ids = knowledge_article_ids || to_jsonb(k.id)
  FROM knowledge_article k
 WHERE s.scenario_code = 'scenario.connection_pool_exhaustion'
   AND k.title = '应用连接池与 max_connections 配置匹配指南'
   AND NOT s.knowledge_article_ids @> to_jsonb(k.id);

UPDATE monitor_scenario s
   SET knowledge_article_ids = knowledge_article_ids || to_jsonb(k.id)
  FROM knowledge_article k
 WHERE s.scenario_code = 'scenario.connection_pool_exhaustion'
   AND k.title = '连接失败与连接被拒的排查方法'
   AND NOT s.knowledge_article_ids @> to_jsonb(k.id);

-- 主从复制风险 += 并行复制配置实践
UPDATE monitor_scenario s
   SET knowledge_article_ids = knowledge_article_ids || to_jsonb(k.id)
  FROM knowledge_article k
 WHERE s.scenario_code = 'scenario.replication_risk'
   AND k.title = 'MySQL 并行复制配置实践（5.6/5.7/8.0）'
   AND NOT s.knowledge_article_ids @> to_jsonb(k.id);

-- SQL 性能劣化 += 慢查询日志配置、吞吐指标解读
UPDATE monitor_scenario s
   SET knowledge_article_ids = knowledge_article_ids || to_jsonb(k.id)
  FROM knowledge_article k
 WHERE s.scenario_code = 'scenario.sql_performance_degradation'
   AND k.title = '慢查询日志配置详解'
   AND NOT s.knowledge_article_ids @> to_jsonb(k.id);

UPDATE monitor_scenario s
   SET knowledge_article_ids = knowledge_article_ids || to_jsonb(k.id)
  FROM knowledge_article k
 WHERE s.scenario_code = 'scenario.sql_performance_degradation'
   AND k.title = 'QPS、TPS 与语句延迟指标解读'
   AND NOT s.knowledge_article_ids @> to_jsonb(k.id);

-- 写入放大 += 容量归档策略
UPDATE monitor_scenario s
   SET knowledge_article_ids = knowledge_article_ids || to_jsonb(k.id)
  FROM knowledge_article k
 WHERE s.scenario_code = 'scenario.write_amplification'
   AND k.title = '表空间容量增长分析与归档策略'
   AND NOT s.knowledge_article_ids @> to_jsonb(k.id);
