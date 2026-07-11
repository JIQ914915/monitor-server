-- =============================================================
-- V120：补充内置场景（§11.8 场景化监控二批，全脚本幂等）
--   5. Buffer Pool 压力（AND）：命中率下降 + 缓冲池已用满 + 语句延迟上升
--      → 区分「内存配置不足/冷数据大范围扫描」与「冷启动预热期的暂时性命中率低」
--   6. 临时表与排序压力（AND）：磁盘临时表增量高 + 环比激增
--      → 指向大结果集排序/GROUP BY 未走索引/tmp_table_size 偏小
--
-- 指标依据（均为 1m 频率、已有采集）：
--   mysql.innodb.buffer_pool_hit_rate   Buffer Pool 命中率（%）
--   mysql.innodb.buffer_pool_usage      Buffer Pool 使用率（%）
--   mysql.perf.avg_stmt_latency_ms      语句平均延迟（ms）
--   mysql.delta.created_tmp_disk_tables 磁盘临时表周期增量（count/min）
-- =============================================================

-- ---- 1. 配套知识库文章（按标题幂等） ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES
  ('Buffer Pool 命中率下降排查', 'performance', '["MySQL","InnoDB","Buffer Pool"]',
   '<h2>典型特征</h2><p>命中率持续低于健康水位（97% 以上为佳）且缓冲池使用率已接近 100%，说明内存不足以承载热数据集，物理读增多、延迟上升。</p><h2>排查步骤</h2><ol><li>确认是否为实例重启后的预热期：预热期命中率低但使用率也低，随时间自行恢复。</li><li>检查近期是否新增大表扫描类查询（报表、导出、全表 COUNT），通过 Top SQL 按扫描行数排序定位。</li><li>对比 <code>innodb_buffer_pool_size</code> 与热数据量：数据量增长跨过内存容量拐点后命中率会阶梯式下降。</li></ol><h2>处置建议</h2><p>优先优化大扫描 SQL 或为其增加合适索引；确认内存不足后再评估调大 <code>innodb_buffer_pool_size</code>（需重启或在线调整，人工确认执行）。</p>'),
  ('InnoDB 内存配置优化建议', 'practice', '["MySQL","InnoDB","参数配置"]',
   '<h2>核心原则</h2><p>专用数据库服务器上 <code>innodb_buffer_pool_size</code> 一般配置为物理内存的 50%~70%，并为操作系统、连接内存（sort/join buffer × 连接数）留足余量。</p><h2>配置要点</h2><ol><li>5.7+ 支持在线调整 buffer pool 大小，按 <code>innodb_buffer_pool_chunk_size</code> 的整数倍调整。</li><li>大内存实例配合 <code>innodb_buffer_pool_instances</code>（每实例约 1GB）降低并发争用。</li><li>调整后观察命中率、脏页比例与刷盘压力，避免一次性调整过大引发换页。</li></ol>'),
  ('磁盘临时表激增的原因与治理', 'performance', '["MySQL","临时表","性能优化"]',
   '<h2>产生机制</h2><p>GROUP BY / ORDER BY / DISTINCT / UNION 等操作的中间结果超过 <code>tmp_table_size</code> 与 <code>max_heap_table_size</code> 较小值，或包含 TEXT/BLOB 列时，内存临时表会转为磁盘临时表，带来明显 IO 开销。</p><h2>排查步骤</h2><ol><li>通过慢查询日志或 Top SQL 定位包含文件排序/临时表标记（Using temporary; Using filesort）的语句。</li><li>用 <code>EXPLAIN</code> 确认分组/排序是否可走索引消除临时表。</li><li>检查 SELECT 是否携带不必要的大字段（TEXT/BLOB）参与排序。</li></ol><h2>处置建议</h2><p>优先改写 SQL 或补充复合索引；确属合理负载时再评估调大 <code>tmp_table_size</code>/<code>max_heap_table_size</code>（会话级验证后再全局调整）。</p>'),
  ('ORDER BY 与 GROUP BY 的索引优化', 'performance', '["MySQL","索引","SQL优化"]',
   '<h2>可走索引的条件</h2><ol><li>排序/分组列的顺序与复合索引列顺序一致（最左前缀）。</li><li>排序方向一致（或 8.0 使用降序索引匹配混合方向）。</li><li>WHERE 等值条件列 + 排序列能拼成同一个索引前缀。</li></ol><h2>验证方法</h2><p><code>EXPLAIN</code> 中不再出现 Using filesort / Using temporary，即表示排序与分组已被索引消除。</p><h2>常见误区</h2><p>对低区分度列单独建索引、函数包裹排序列、隐式类型转换都会使索引失效。</p>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 2. 内置场景 ----

-- [5] Buffer Pool 压力（预警，AND）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.buffer_pool_pressure',
    'Buffer Pool 压力',
    '命中率、使用率、语句延迟三个信号联合判断：命中率下降且缓冲池已用满、延迟同步上升才触发，排除实例重启后预热期（此时使用率低）的暂时性命中率下降误报',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":300,"children":[
       {"type":"condition","code":"bp_hit_rate","name":"Buffer Pool 命中率","metricCode":"mysql.innodb.buffer_pool_hit_rate",
        "condType":"threshold","operator":"<=","threshold":95,"unit":"%","exprText":"≤ 95%"},
       {"type":"condition","code":"bp_usage","name":"Buffer Pool 使用率","metricCode":"mysql.innodb.buffer_pool_usage",
        "condType":"threshold","operator":">=","threshold":90,"unit":"%","exprText":"≥ 90%（排除预热期）"},
       {"type":"condition","code":"latency_high","name":"语句平均延迟","metricCode":"mysql.perf.avg_stmt_latency_ms",
        "condType":"threshold","operator":">=","threshold":200,"unit":"ms","exprText":"≥ 200ms"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    'Buffer Pool 命中率下降且缓冲池已用满，语句延迟同步上升，指向内存不足以承载热数据或存在大范围冷数据扫描，建议先通过 Top SQL 定位大扫描语句，再评估 innodb_buffer_pool_size 配置',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- [6] 临时表与排序压力（预警，AND）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.tmp_table_pressure',
    '临时表与排序压力',
    '磁盘临时表增量绝对值与环比双条件联合判断：增量高且较 1 小时前明显激增才触发，区分「常态化的报表类负载」与「新上线 SQL 引发的排序/分组劣化」',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":180,"children":[
       {"type":"condition","code":"disk_tmp_high","name":"磁盘临时表增量","metricCode":"mysql.delta.created_tmp_disk_tables",
        "condType":"threshold","operator":">=","threshold":20,"unit":"count","exprText":"≥ 20/分钟"},
       {"type":"condition","code":"disk_tmp_surge","name":"磁盘临时表环比","metricCode":"mysql.delta.created_tmp_disk_tables",
        "condType":"rate_change","compareOffset":"1h","operator":">=","threshold":100,"unit":"%",
        "exprText":"较1小时前增加 ≥ 100%"}
     ]}'::jsonb,
    '{"duration":300}'::jsonb,
    '磁盘临时表创建量激增，通常由大结果集排序、GROUP BY/DISTINCT 未走索引或 SELECT 携带大字段导致，建议定位含 Using temporary/Using filesort 的语句并优化索引，必要时评估 tmp_table_size 配置',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 3. 场景关联知识库文章（按标题回查 id，重复执行会刷新关联） ----
UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('Buffer Pool 命中率下降排查', 'InnoDB 内存配置优化建议'))
 WHERE s.scenario_code = 'scenario.buffer_pool_pressure';

UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('磁盘临时表激增的原因与治理', 'ORDER BY 与 GROUP BY 的索引优化', 'Top SQL 分析方法'))
 WHERE s.scenario_code = 'scenario.tmp_table_pressure';
