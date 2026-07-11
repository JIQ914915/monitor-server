-- =============================================================
-- V121：补充内置场景「写入放大 / Binlog 暴增」（§11.8 场景化监控，全脚本幂等）
--
-- 设计（AND，持续 10 分钟触发）：
--   tps_surge    ：TPS 较 1 小时前上涨 ≥ 100%（写入速率翻倍，捕捉批量/异常写入）
--   binlog_surge ：Binlog 总占用较 1 天前增加 ≥ 50%（确认写入已转化为日志膨胀，非瞬时尖峰）
--
-- 已知限制（有意取舍）：
--   1. 复制延迟不进 AND 组：单机实例无 seconds_behind 指标，缺失会令场景恒为 UNKNOWN，
--      延迟影响写入诊断结论提示人工确认；
--   2. binlog 清理（expire_logs）后 total_bytes 回落，环比为负不会误触发，
--      但清理后短期内可能漏报，由 TPS 信号双确认缓解；
--   3. 未开 binlog 的实例指标缺失 → UNKNOWN，不误报。
--
-- 指标依据（均已采集）：
--   mysql.tps                1m   写入事务速率
--   mysql.binlog.total_bytes 1h   Binlog 总占用（环比基准取 1 天前，容差 90 分钟）
-- =============================================================

-- ---- 1. 配套知识库文章（按标题幂等） ----
INSERT INTO knowledge_article (title, category, tags, content, author, views, likes)
SELECT v.title, v.category, v.tags::jsonb, v.content, 'system', 0, 0
FROM (VALUES
  ('Binlog 暴增的常见原因与排查', 'fault', '["MySQL","Binlog","磁盘空间"]',
   '<h2>常见原因</h2><ol><li>批量 DML：大范围 UPDATE/DELETE 在 row 格式下逐行记录，日志量远超语句本身。</li><li>刷数任务/数据订正脚本未分批提交。</li><li><code>binlog_format=ROW</code> 且缺少主键的表全表更新，产生完整前后镜像。</li><li><code>expire_logs_days</code>（8.0 为 <code>binlog_expire_logs_seconds</code>）配置过长，清理不及时。</li></ol><h2>排查步骤</h2><ol><li><code>SHOW BINARY LOGS</code> 查看各文件生成时间密度，定位暴增时段。</li><li>用 <code>mysqlbinlog --base64-output=decode-rows -vv</code> 抽样解析暴增时段日志，定位来源表与语句类型。</li><li>结合 Top SQL 与慢查询定位对应的批量任务。</li></ol><h2>处置建议</h2><p>推动批量任务分批提交、错峰执行；确认磁盘余量，必要时人工执行 <code>PURGE BINARY LOGS</code>（需确认从库已同步）。</p>'),
  ('大事务对复制与磁盘的影响', 'practice', '["MySQL","大事务","复制"]',
   '<h2>影响链路</h2><ul><li>主库：大事务提交前 binlog cache 落盘，提交瞬间产生巨量日志写入，挤占 IO。</li><li>从库：单个大事务串行回放，期间复制延迟持续攀升，且无法并行。</li><li>磁盘：binlog/undo 同步膨胀，极端情况下撑满磁盘导致实例拒写。</li></ul><h2>识别方法</h2><ol><li>监控 TPS 与 binlog 增速的联动突变。</li><li><code>information_schema.innodb_trx</code> 中 <code>trx_rows_modified</code> 大的活跃事务。</li></ol><h2>治理建议</h2><p>批量操作按主键范围分批（每批 1 千~1 万行）提交；避免一条语句更新全表；变更前评估影响行数。</p>')
) AS v(title, category, tags, content)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_article k WHERE k.title = v.title);

-- ---- 2. 内置场景 ----

-- [7] 写入放大 / Binlog 暴增（预警，AND）
INSERT INTO monitor_scenario (scenario_code, scenario_name, description, severity, db_type_id, db_version_ids,
    condition_config, recovery_config, diagnosis_template, diagnosis_branches, scan_interval_min, builtin)
VALUES (
    'scenario.write_amplification',
    '写入放大 / Binlog 暴增',
    'TPS 环比与 Binlog 增长环比双信号联合判断：写入速率翻倍且日志占用较昨日明显膨胀才触发，捕捉批量写入、数据订正任务和大事务引发的磁盘增长加速与复制延迟前兆，排除瞬时写入尖峰误报',
    'level_3',
    (SELECT id FROM database_type WHERE code = 'MYSQL'),
    NULL,
    '{"logic":"AND","duration":600,"children":[
       {"type":"condition","code":"tps_surge","name":"TPS 环比","metricCode":"mysql.tps",
        "condType":"rate_change","compareOffset":"1h","operator":">=","threshold":100,"unit":"%",
        "exprText":"较1小时前上涨 ≥ 100%"},
       {"type":"condition","code":"binlog_surge","name":"Binlog 增长环比","metricCode":"mysql.binlog.total_bytes",
        "condType":"rate_change","compareOffset":"1d","operator":">=","threshold":50,"unit":"%",
        "exprText":"较1天前增加 ≥ 50%"}
     ]}'::jsonb,
    '{"duration":600}'::jsonb,
    '写入速率与 Binlog 占用同步激增，指向批量写入任务、数据订正脚本或大事务（row 格式下全表 UPDATE 放大明显），建议定位来源任务并分批提交，同时关注磁盘余量与从库复制延迟是否被拉高',
    NULL,
    1, TRUE
) ON CONFLICT (scenario_code) DO NOTHING;

-- ---- 3. 场景关联知识库文章（按标题回查 id，重复执行会刷新关联） ----
UPDATE monitor_scenario s
   SET knowledge_article_ids = (
        SELECT COALESCE(jsonb_agg(k.id ORDER BY k.id), '[]'::jsonb)
          FROM knowledge_article k
         WHERE k.title IN ('Binlog 暴增的常见原因与排查', '大事务对复制与磁盘的影响', '从库回放慢的常见原因'))
 WHERE s.scenario_code = 'scenario.write_amplification';
