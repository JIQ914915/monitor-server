-- =============================================================
-- V127：告警下钻画像库化（需求 §11.7 事件下钻）
--   1. alert_drilldown_profile  告警类型画像表：按触发指标编码匹配，
--      驱动下钻页的关联指标 / 可能原因 / 排查路径 / 建议动作四块内容
--   2. 内置画像种子数据（原前端 alertDrilldown.ts 配置迁入）：
--      连接与会话 / SQL性能 / 锁与事务 / 容量空间 / 高可用复制 / 缓存 / 可用性采集 / 通用兜底
--   3. 系统级菜单「下钻画像」+ 预设角色权限
-- 注：全脚本幂等（IF NOT EXISTS / ON CONFLICT / 存在性守卫）
-- =============================================================

-- ---- 1. 画像表 ----
CREATE TABLE IF NOT EXISTS alert_drilldown_profile (
    id              BIGSERIAL    PRIMARY KEY,
    profile_code    VARCHAR(64)  NOT NULL UNIQUE,          -- 如 connections / slowsql / generic
    profile_label   VARCHAR(64)  NOT NULL,                 -- 展示名：连接与会话类
    db_type         VARCHAR(32)  NOT NULL DEFAULT 'mysql', -- 适用数据库类型（预留多类型扩展）
    match_rules     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    related_metrics JSONB        NOT NULL DEFAULT '[]'::jsonb,
    causes          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    steps           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    actions         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    builtin         BOOLEAN      NOT NULL DEFAULT FALSE,   -- 内置画像不可删除
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort            INT          NOT NULL DEFAULT 0,
    remark          VARCHAR(255),
    created_at      TIMESTAMPTZ  DEFAULT now(),
    updated_at      TIMESTAMPTZ  DEFAULT now()
);
COMMENT ON TABLE alert_drilldown_profile IS '告警下钻画像（§11.7）：按告警触发指标编码匹配，驱动下钻页关联指标/可能原因/排查路径/建议动作';
COMMENT ON COLUMN alert_drilldown_profile.match_rules IS '匹配规则数组 [{"matchType":"exact|prefix","pattern":"mysql.conn."}]；exact 优先于 prefix，prefix 长者优先；空数组=仅作兜底（generic）';
COMMENT ON COLUMN alert_drilldown_profile.related_metrics IS '关联指标 [{"code","label","unit","color","toGB"}]，unit 为展示符号（%、s、页/s…），toGB=字节转 GB 展示';
COMMENT ON COLUMN alert_drilldown_profile.causes IS '可能原因 [{"cause","confidence"(0-1),"color"(danger/warning/info),"evidence":["…{value}{threshold}{unit} 占位符由页面替换…"]}]';
COMMENT ON COLUMN alert_drilldown_profile.steps IS '排查路径 [{"title","description","action","link"}]，link 为页面编码（slowsql/realtime/performance/scenario/knowledge/collector），前端映射到路由';
COMMENT ON COLUMN alert_drilldown_profile.actions IS '建议动作 [{"action","risk"(low/medium/high),"description","sql","impact"}]，仅辅助决策，系统不自动执行';

-- ---- 2. 内置画像种子数据 ----

-- 2.1 连接与会话类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'connections', '连接与会话类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.conn."},
  {"matchType":"prefix","pattern":"mysql.connections"},
  {"matchType":"prefix","pattern":"mysql.status.threads"},
  {"matchType":"prefix","pattern":"mysql.status.Threads"},
  {"matchType":"prefix","pattern":"mysql.delta.threads_connected"},
  {"matchType":"prefix","pattern":"mysql.delta.aborted_connects"},
  {"matchType":"prefix","pattern":"mysql.delta.connection_errors"}
]$J$::jsonb,
$J$[
  {"code":"mysql.conn.total","label":"当前连接数","unit":"","color":"#0C7C97"},
  {"code":"mysql.conn.usage","label":"连接使用率","unit":"%","color":"#E08600"},
  {"code":"mysql.conn.active","label":"活跃连接数","unit":"","color":"#15A36A"},
  {"code":"mysql.delta.slow_queries","label":"慢SQL数/分钟","unit":"","color":"#E5484D"},
  {"code":"mysql.qps","label":"QPS","unit":"","color":"#6366F1"},
  {"code":"mysql.conn.state.locked","label":"等锁连接数","unit":"","color":"#9B59B6"}
]$J$::jsonb,
$J$[
  {"cause":"慢SQL积压导致连接堆积","confidence":0.9,"color":"danger","evidence":["慢SQL数量如同步增长，说明查询长时间占用连接未释放","当前值 {value}{unit} 持续超过阈值 {threshold}{unit}","QPS 无明显波动时可排除流量激增原因"]},
  {"cause":"锁等待/长事务阻塞连接释放","confidence":0.75,"color":"warning","evidence":["等锁连接数上升说明存在行锁等待，被阻塞的连接无法归还","建议结合锁与事务指标确认阻塞源"]},
  {"cause":"应用连接池配置不合理或泄漏","confidence":0.5,"color":"info","evidence":["应用可能未设置连接超时或超时值过大","连接池上限之和可能超出数据库 max_connections 承载能力"]}
]$J$::jsonb,
$J$[
  {"title":"查看慢SQL列表","description":"确认是否有大量慢SQL导致连接堆积","action":"前往慢SQL分析","link":"slowsql"},
  {"title":"检查活跃连接来源","description":"在实时概况查看连接来源 Top 与活跃会话，定位异常来源主机/账号","action":"查看实时概况","link":"realtime"},
  {"title":"确认锁等待情况","description":"查看锁与事务类指标，确认是否存在行锁等待阻塞连接释放","action":"前往性能分析","link":"performance"},
  {"title":"评估连接池配置","description":"核对应用连接池上限、超时配置与数据库 max_connections 是否匹配","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"终止超时空闲连接","risk":"medium","description":"清理长时间空闲（如超过 5 分钟）的连接","sql":"-- 查询空闲超过5分钟的连接\nSELECT id, user, host, time, state FROM information_schema.processlist\nWHERE command = 'Sleep' AND time > 300;\n-- 终止指定连接（替换 <id>）\nKILL <id>;","impact":"请先确认是否为业务正常连接，避免误杀"},
  {"action":"临时调整最大连接数","risk":"medium","description":"临时扩大连接上限缓解压力（治标不治本）","sql":"-- 查看当前设置\nSHOW VARIABLES LIKE \"max_connections\";\n-- 临时调整（重启后失效）\nSET GLOBAL max_connections = 2000;","impact":"需同步优化慢SQL，否则问题会反复出现"},
  {"action":"优化Top慢SQL（添加索引）","risk":"low","description":"为高频慢SQL所用字段添加合适索引","sql":"-- 查看慢SQL Top10\nSELECT * FROM performance_schema.events_statements_summary_by_digest\nORDER BY SUM_TIMER_WAIT DESC LIMIT 10;\n-- 添加索引示例\nCREATE INDEX idx_col ON table_name(column_name);","impact":"建议在业务低峰期执行，避免锁表"}
]$J$::jsonb,
TRUE, TRUE, 1, '连接使用率/连接数/活跃线程/连接异常类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'connections');

-- 2.2 SQL 性能类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'slowsql', 'SQL 性能类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.delta.slow_queries"},
  {"matchType":"prefix","pattern":"mysql.perf."},
  {"matchType":"prefix","pattern":"mysql.delta.created_tmp"}
]$J$::jsonb,
$J$[
  {"code":"mysql.delta.slow_queries","label":"慢SQL数/分钟","unit":"","color":"#E5484D"},
  {"code":"mysql.perf.avg_stmt_latency_ms","label":"平均响应时间","unit":"ms","color":"#E08600"},
  {"code":"mysql.innodb.lock_waits","label":"锁等待数","unit":"","color":"#9B59B6"},
  {"code":"mysql.delta.created_tmp_disk_tables","label":"磁盘临时表","unit":"","color":"#6366F1"},
  {"code":"mysql.qps","label":"QPS","unit":"","color":"#0C7C97"},
  {"code":"mysql.conn.total","label":"当前连接数","unit":"","color":"#15A36A"}
]$J$::jsonb,
$J$[
  {"cause":"SQL变更或执行计划退化","confidence":0.85,"color":"danger","evidence":["慢SQL数量突然增加（当前值 {value}{unit}，阈值 {threshold}{unit}），常见于近期发布变更","统计信息陈旧可能导致部分SQL执行计划变差"]},
  {"cause":"热点行锁等待阻塞","confidence":0.7,"color":"warning","evidence":["锁等待数如同步上升，说明阻塞导致响应时间被拉长","建议在慢SQL详情中查看等待时间占比"]},
  {"cause":"临时表与排序操作过多","confidence":0.5,"color":"info","evidence":["磁盘临时表持续偏高说明 SQL 需优化或 tmp_table_size 偏小"]}
]$J$::jsonb,
$J$[
  {"title":"确认Top SQL排名","description":"按执行次数和平均耗时排序慢SQL列表，锁定最值得优化的语句","action":"前往慢SQL分析","link":"slowsql"},
  {"title":"查看执行计划","description":"在慢SQL详情中执行 EXPLAIN，确认索引使用与扫描行数","action":"执行EXPLAIN分析","link":"slowsql"},
  {"title":"检查锁等待与长事务","description":"确认是否有行锁阻塞导致响应时间上升","action":"前往性能分析","link":"performance"},
  {"title":"确认近期发布变更","description":"回顾是否有SQL或表结构变更导致执行计划退化","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"刷新统计信息","risk":"low","description":"对Top慢SQL涉及的表刷新统计信息","sql":"ANALYZE TABLE table_name;","impact":"会短暂加读锁，影响极小"},
  {"action":"添加缺失索引","risk":"low","description":"根据执行计划为慢SQL添加合适索引","sql":"-- EXPLAIN 检查索引使用\nEXPLAIN SELECT ...;\n-- 创建索引\nCREATE INDEX idx_xxx ON table_name(col1, col2);","impact":"建议低峰期执行，大表建议使用 pt-online-schema-change"},
  {"action":"终止明确异常的长查询","risk":"high","description":"对确认异常且影响面大的长时间查询执行终止","sql":"-- 查找长时间运行查询\nSELECT id, user, time, state, info FROM information_schema.processlist\nWHERE command != 'Sleep' ORDER BY time DESC LIMIT 20;\n-- 终止（替换 <id>）\nKILL QUERY <id>;","impact":"终止查询会影响对应业务请求，需与业务确认后执行"}
]$J$::jsonb,
TRUE, TRUE, 2, '慢SQL增量/响应时间/临时表类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'slowsql');

-- 2.3 锁与事务类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'lock', '锁与事务类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.innodb."},
  {"matchType":"prefix","pattern":"mysql.lock_timeout"}
]$J$::jsonb,
$J$[
  {"code":"mysql.innodb.lock_waits","label":"锁等待数","unit":"","color":"#E5484D"},
  {"code":"mysql.innodb.blocked_sessions","label":"阻塞会话数","unit":"","color":"#9B59B6"},
  {"code":"mysql.innodb.deadlock_count","label":"死锁次数","unit":"","color":"#B91C1C"},
  {"code":"mysql.innodb.trx_max_seconds","label":"最长事务时长","unit":"s","color":"#E08600"},
  {"code":"mysql.innodb.trx_active","label":"活跃事务数","unit":"","color":"#0C7C97"},
  {"code":"mysql.delta.slow_queries","label":"慢SQL数/分钟","unit":"","color":"#6366F1"}
]$J$::jsonb,
$J$[
  {"cause":"长事务未提交持有锁","confidence":0.85,"color":"danger","evidence":["最长事务时长如显著偏高，说明有事务长时间未提交、持续持有行锁","当前值 {value}{unit}，阈值 {threshold}{unit}"]},
  {"cause":"热点行并发更新竞争","confidence":0.7,"color":"warning","evidence":["多个会话集中更新同一行/同一批行（如库存、计数器场景）","锁等待与阻塞会话数同步上升"]},
  {"cause":"批量事务范围过大或更新顺序不一致","confidence":0.5,"color":"info","evidence":["大批量 UPDATE/DELETE 长时间持锁","不同事务以不同顺序更新相同资源时容易死锁"]}
]$J$::jsonb,
$J$[
  {"title":"确认阻塞源","description":"在实时概况查看当前会话，找出持锁最久的根阻塞事务","action":"查看实时概况","link":"realtime"},
  {"title":"查看事务开始时间与SQL","description":"确认阻塞事务运行的语句与开始时间，评估业务影响","action":"前往性能分析","link":"performance"},
  {"title":"检查关联慢SQL","description":"锁等待常伴随慢SQL，确认是否同一批语句","action":"前往慢SQL分析","link":"slowsql"},
  {"title":"评估处理方式","description":"联系业务确认后再决定等待提交或终止事务","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"定位阻塞链与根阻塞事务","risk":"low","description":"查询当前锁等待关系，确定谁在阻塞谁","sql":"-- MySQL 8.0\nSELECT * FROM sys.innodb_lock_waits\\G\n-- MySQL 5.7\nSELECT r.trx_id waiting_trx, r.trx_mysql_thread_id waiting_thread,\n       b.trx_id blocking_trx, b.trx_mysql_thread_id blocking_thread\nFROM information_schema.innodb_lock_waits w\nJOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id\nJOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;","impact":"只读查询，无风险"},
  {"action":"终止确认异常的阻塞事务","risk":"high","description":"对确认异常（如忘记提交）的根阻塞事务执行终止","sql":"-- 查看长事务\nSELECT trx_id, trx_started, trx_mysql_thread_id, trx_query\nFROM information_schema.innodb_trx ORDER BY trx_started LIMIT 10;\n-- 终止（替换 <thread_id>）\nKILL <thread_id>;","impact":"终止会回滚该事务全部修改，务必先与业务确认"},
  {"action":"优化事务范围与更新顺序","risk":"low","description":"缩短事务、分批提交、统一更新顺序，从根上减少锁竞争","sql":"-- 大批量更新改为分批提交示例\nUPDATE orders SET status = 1 WHERE id BETWEEN 1 AND 1000;\nCOMMIT;","impact":"需要业务代码配合改造，建议纳入变更计划"}
]$J$::jsonb,
TRUE, TRUE, 3, '锁等待/死锁/长事务类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'lock');

-- 2.4 容量空间类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'capacity', '容量空间类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.capacity."},
  {"matchType":"prefix","pattern":"mysql.binlog."}
]$J$::jsonb,
$J$[
  {"code":"mysql.capacity.total_size_bytes","label":"库表总容量","unit":"GB","color":"#9B59B6","toGB":true},
  {"code":"mysql.capacity.data_size_bytes","label":"数据容量","unit":"GB","color":"#0C7C97","toGB":true},
  {"code":"mysql.capacity.index_size_bytes","label":"索引容量","unit":"GB","color":"#15A36A","toGB":true},
  {"code":"mysql.binlog.total_bytes","label":"Binlog占用","unit":"GB","color":"#6366F1","toGB":true}
]$J$::jsonb,
$J$[
  {"cause":"Binlog/日志未及时清理","confidence":0.8,"color":"danger","evidence":["Binlog 占用如持续增长，多为保留时间设置过长或清理任务异常","建议确认 binlog_expire_logs_seconds（8.0）或 expire_logs_days（5.6/5.7）"]},
  {"cause":"数据自然增长超出预期","confidence":0.7,"color":"warning","evidence":["当前值 {value}{unit}，已达到阈值 {threshold}{unit}","近期写入量高于历史平均时增长会加速"]},
  {"cause":"大表膨胀或批量导入","confidence":0.5,"color":"info","evidence":["结合容量 Top 表确认增长最快的对象","批量导入、日志表未归档是常见诱因"]}
]$J$::jsonb,
$J$[
  {"title":"确认增长最快对象","description":"在实时概况查看表空间 Top10，锁定占用与增长最大的表","action":"查看实时概况","link":"realtime"},
  {"title":"检查 Binlog 保留策略","description":"确认 Binlog 过期配置与实际占用，评估可清理空间","action":"前往性能分析","link":"performance"},
  {"title":"评估归档与扩容计划","description":"根据增长速率预测耗尽时间，制定归档/扩容方案","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"清理过期 Binlog","risk":"high","description":"手动清理已确认不再需要的旧 Binlog","sql":"-- 查看Binlog文件\nSHOW BINARY LOGS;\n-- 按日期清理\nPURGE BINARY LOGS BEFORE DATE_SUB(NOW(), INTERVAL 3 DAY);","impact":"清理前请确认从库已同步到该位点，否则会导致复制中断"},
  {"action":"归档历史数据","risk":"medium","description":"将历史表数据迁移至归档库或分区","sql":"-- 示例：归档3个月前的订单数据\nINSERT INTO orders_archive SELECT * FROM orders\n  WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);\nDELETE FROM orders WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY) LIMIT 1000;","impact":"分批删除避免长事务，建议在低峰期执行"},
  {"action":"磁盘扩容","risk":"medium","description":"按增长速率评估扩容规格，走线下变更流程","sql":"-- 预估近30天增长速率后确定扩容目标\n-- 扩容属于基础设施变更，请走线下审批流程执行","impact":"需要停机窗口或在线扩容能力支持"}
]$J$::jsonb,
TRUE, TRUE, 4, '表空间/Binlog占用类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'capacity');

-- 2.5 高可用/复制类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'replication', '高可用/复制类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.replication."},
  {"matchType":"prefix","pattern":"mysql.gr."},
  {"matchType":"prefix","pattern":"mysql.group_replication"}
]$J$::jsonb,
$J$[
  {"code":"mysql.replication.seconds_behind","label":"复制延迟","unit":"s","color":"#E5484D"},
  {"code":"mysql.replication.io_running","label":"IO 线程（0/1）","unit":"","color":"#0C7C97"},
  {"code":"mysql.replication.sql_running","label":"SQL 线程（0/1）","unit":"","color":"#15A36A"},
  {"code":"mysql.tps","label":"主库写入TPS","unit":"","color":"#E08600"},
  {"code":"mysql.innodb.trx_max_seconds","label":"最长事务时长","unit":"s","color":"#9B59B6"}
]$J$::jsonb,
$J$[
  {"cause":"主库写入峰值导致从库回放跟不上","confidence":0.85,"color":"danger","evidence":["当前延迟 {value}{unit}，超过阈值 {threshold}{unit}","TPS 如同步上升，说明单线程回放成为瓶颈"]},
  {"cause":"复制线程异常停止","confidence":0.7,"color":"warning","evidence":["IO/SQL 线程任一掉 0 即复制中断，延迟会持续累积","常见原因：主从数据冲突、账号权限变更、网络中断"]},
  {"cause":"从库资源不足或大事务回放","confidence":0.55,"color":"info","evidence":["从库磁盘 I/O 或 CPU 达到瓶颈时回放变慢","主库大事务（大批量更新）在从库需同样时长回放"]}
]$J$::jsonb,
$J$[
  {"title":"确认同步线程状态","description":"查看 IO/SQL 线程是否正常运行，有无复制错误","action":"查看实时概况","link":"realtime"},
  {"title":"检查主库写入TPS","description":"确认主库写入量是否超出从库回放能力","action":"前往性能分析","link":"performance"},
  {"title":"排查大事务","description":"确认是否有大批量事务正在回放","action":"前往性能分析","link":"performance"},
  {"title":"评估并行复制","description":"延迟长期偏高时评估开启并行复制提升回放效率","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"检查复制状态与错误","risk":"low","description":"确认线程状态、位点差与最近错误信息","sql":"-- MySQL 8.0.22+\nSHOW REPLICA STATUS\\G\n-- 旧版本\nSHOW SLAVE STATUS\\G","impact":"只读查询，无风险"},
  {"action":"重启复制线程","risk":"medium","description":"若复制线程已停止，确认错误原因后尝试重启","sql":"-- 确认错误原因后执行\nSTOP REPLICA; START REPLICA;\n-- 旧版本\nSTOP SLAVE; START SLAVE;","impact":"重启前请先确认复制错误原因，避免跳过重要事务"},
  {"action":"开启并行复制","risk":"medium","description":"增大并行回放线程数提升从库回放速度","sql":"-- 建议在从库执行（5.7+）\nSET GLOBAL slave_parallel_type = \"LOGICAL_CLOCK\";\nSET GLOBAL slave_parallel_workers = 4;","impact":"开启后需监控从库CPU和复制延迟变化；延迟恢复前禁止自动主从切换"}
]$J$::jsonb,
TRUE, TRUE, 5, '复制延迟/复制线程/组复制类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'replication');

-- 2.6 缓存类（Buffer Pool 相关指标须先于 2.3 的 mysql.innodb. 前缀命中，靠更长前缀实现）
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'cache', '缓存类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.innodb.buffer_pool"},
  {"matchType":"prefix","pattern":"mysql.innodb.dirty_page"},
  {"matchType":"prefix","pattern":"mysql.rate.Innodb_buffer_pool"}
]$J$::jsonb,
$J$[
  {"code":"mysql.innodb.buffer_pool_hit_rate","label":"Buffer Pool命中率","unit":"%","color":"#15A36A"},
  {"code":"mysql.innodb.buffer_pool_usage","label":"Buffer Pool使用率","unit":"%","color":"#0C7C97"},
  {"code":"mysql.innodb.dirty_page_ratio","label":"脏页比例","unit":"%","color":"#E08600"},
  {"code":"mysql.rate.Innodb_buffer_pool_pages_flushed","label":"刷页速率","unit":"页/s","color":"#6366F1"},
  {"code":"mysql.qps","label":"QPS","unit":"","color":"#9B59B6"}
]$J$::jsonb,
$J$[
  {"cause":"Buffer Pool 容量不足导致频繁换页","confidence":0.8,"color":"danger","evidence":["命中率降至 {value}{unit}，低于阈值 {threshold}{unit}","热数据集超过缓冲池容量时命中率持续走低"]},
  {"cause":"全表扫描污染 Buffer Pool","confidence":0.65,"color":"warning","evidence":["无索引的大表扫描会将大量冷数据载入缓冲池、淘汰热数据","常伴随慢SQL数上升"]},
  {"cause":"实例重启后的缓存预热期","confidence":0.4,"color":"info","evidence":["重启后缓冲池为空，命中率随访问逐步回升属正常现象"]}
]$J$::jsonb,
$J$[
  {"title":"确认Buffer Pool使用情况","description":"查看使用率与脏页比例，判断是容量不足还是写入压力","action":"前往性能分析","link":"performance"},
  {"title":"定位大表全扫SQL","description":"找出触发全表扫描的SQL，考虑增加索引或分页优化","action":"前往慢SQL分析","link":"slowsql"},
  {"title":"评估Buffer Pool扩容","description":"如物理内存充足，可适当调大 innodb_buffer_pool_size","action":"查看知识库","link":"knowledge"}
]$J$::jsonb,
$J$[
  {"action":"调大 Buffer Pool","risk":"medium","description":"若内存充足，适当扩大 innodb_buffer_pool_size","sql":"-- 查看当前值\nSHOW VARIABLES LIKE \"innodb_buffer_pool_size\";\n-- 动态调整（MySQL 5.7+，示例 4GB）\nSET GLOBAL innodb_buffer_pool_size = 4294967296;","impact":"调整前确认服务器物理内存余量，建议不超过总内存70%"},
  {"action":"优化大表全扫SQL","risk":"low","description":"添加索引或改写SQL避免全表扫描","sql":"-- 找出全扫SQL\nSELECT DIGEST_TEXT FROM performance_schema.events_statements_summary_by_digest\nWHERE SUM_NO_INDEX_USED > 0 ORDER BY SUM_ROWS_EXAMINED DESC LIMIT 5;","impact":"减少冷数据进入Buffer Pool，提升命中率"}
]$J$::jsonb,
TRUE, TRUE, 6, 'Buffer Pool 命中率/脏页/刷页类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'cache');

-- 2.7 可用性/采集类
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'availability', '可用性/采集类', 'mysql',
$J$[
  {"matchType":"prefix","pattern":"mysql.availability"},
  {"matchType":"prefix","pattern":"mysql.instance.restarted"},
  {"matchType":"prefix","pattern":"mysql.errorlog."},
  {"matchType":"prefix","pattern":"system.connection_failure"}
]$J$::jsonb,
$J$[
  {"code":"mysql.conn.total","label":"当前连接数","unit":"","color":"#0C7C97"},
  {"code":"mysql.qps","label":"QPS","unit":"","color":"#15A36A"},
  {"code":"mysql.delta.aborted_connects","label":"连接失败数/分钟","unit":"","color":"#E5484D"},
  {"code":"mysql.errorlog.error_count","label":"错误日志 Error 数","unit":"","color":"#E08600"}
]$J$::jsonb,
$J$[
  {"cause":"实例停机或网络不可达","confidence":0.8,"color":"danger","evidence":["采集连续失败通常意味着实例宕机、端口不通或网络中断","趋势图在故障时段会出现数据断档"]},
  {"cause":"监控账号失效或权限变更","confidence":0.6,"color":"warning","evidence":["密码过期、账号被锁、权限回收都会导致采集失败","错误信息含 Access denied 时基本可确认"]},
  {"cause":"实例过载导致连接被拒","confidence":0.5,"color":"info","evidence":["连接打满（Too many connections）时监控连接同样会被拒绝","恢复后应回查连接与慢SQL指标"]}
]$J$::jsonb,
$J$[
  {"title":"确认实例存活与网络","description":"先在主机上确认 mysqld 进程与端口连通性","action":"查看实时概况","link":"realtime"},
  {"title":"检查监控账号权限","description":"用监控账号手动登录验证密码与权限","action":"查看知识库","link":"knowledge"},
  {"title":"检查采集任务日志","description":"查看采集任务的错误信息，确认失败原因分类","action":"查看采集任务","link":"collector"}
]$J$::jsonb,
$J$[
  {"action":"验证监控账号连通性","risk":"low","description":"手动使用监控账号连接目标库验证","sql":"-- 在监控服务器上执行\nmysql -h <host> -P <port> -u monitor_user -p -e \"SELECT 1;\"","impact":"只读验证，无风险"},
  {"action":"恢复监控账号权限","risk":"medium","description":"账号失效时重建授权","sql":"-- 在目标库执行\nGRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO 'monitor_user'@'%';\nFLUSH PRIVILEGES;","impact":"权限变更请遵循最小授权原则"}
]$J$::jsonb,
TRUE, TRUE, 7, '实例不可连接/重启/错误日志类告警'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'availability');

-- 2.8 通用兜底（match_rules 为空 = 仅在其他画像均未命中时使用）
INSERT INTO alert_drilldown_profile (profile_code, profile_label, db_type, match_rules, related_metrics, causes, steps, actions, builtin, enabled, sort, remark)
SELECT 'generic', '通用类', 'mysql',
'[]'::jsonb,
$J$[
  {"code":"mysql.qps","label":"QPS","unit":"","color":"#0C7C97"},
  {"code":"mysql.conn.total","label":"当前连接数","unit":"","color":"#15A36A"},
  {"code":"mysql.delta.slow_queries","label":"慢SQL数/分钟","unit":"","color":"#E5484D"},
  {"code":"mysql.perf.avg_stmt_latency_ms","label":"平均响应时间","unit":"ms","color":"#E08600"}
]$J$::jsonb,
$J$[
  {"cause":"指标超过阈值，请结合关联指标趋势进一步分析","confidence":0.5,"color":"info","evidence":["当前值 {value}{unit}，阈值 {threshold}{unit}","观察告警前后其他指标是否同步异动"]}
]$J$::jsonb,
$J$[
  {"title":"分析指标趋势","description":"查看告警前后相关指标变化，寻找同步异动的信号","action":"前往性能分析","link":"performance"},
  {"title":"检查实例整体状态","description":"确认连接、慢SQL、锁等核心指标状态","action":"查看实时概况","link":"realtime"}
]$J$::jsonb,
$J$[
  {"action":"结合性能分析定位","risk":"low","description":"在性能分析页对照多类指标趋势，缩小问题范围","sql":"-- 请在性能分析页面查看趋势图","impact":"无操作风险"}
]$J$::jsonb,
TRUE, TRUE, 99, '未命中任何画像时的兜底'
WHERE NOT EXISTS (SELECT 1 FROM alert_drilldown_profile WHERE profile_code = 'generic');

-- ---- 3. 系统级菜单：系统设置 → 下钻画像（排在知识库之后） ----
INSERT INTO sys_menu (name, code, menu_type, type, icon, route, component, perm, sort, status, visible, parent_id, description, buttons)
VALUES ('下钻画像', 'drilldown_profile', 'menu', '系统级', 'Aim',
        'drilldown-profile', 'system/drilldown-profile',
        'drilldown_profile:view', 20, 'enabled', TRUE,
        (SELECT id FROM sys_menu WHERE code = 'system'),
        '告警下钻画像管理：按触发指标编码匹配，维护下钻页的关联指标/可能原因/排查路径/建议动作',
        '[
          {"name": "新增", "code": "drilldown_profile:create", "status": "enabled"},
          {"name": "编辑", "code": "drilldown_profile:update", "status": "enabled"},
          {"name": "删除", "code": "drilldown_profile:delete", "status": "enabled"},
          {"name": "启停", "code": "drilldown_profile:toggle", "status": "enabled"}
        ]'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- ---- 4. 预设角色权限 ----
-- super_admin 已拥有 *:*，无需单独添加

-- dba：完整管理
UPDATE sys_role
   SET permissions = permissions
       || '["drilldown_profile:view", "drilldown_profile:create", "drilldown_profile:update", "drilldown_profile:delete", "drilldown_profile:toggle"]'::jsonb
 WHERE code = 'dba'
   AND NOT (permissions @> '["drilldown_profile:view"]'::jsonb);

-- ops / auditor：仅查看
UPDATE sys_role
   SET permissions = permissions
       || '["drilldown_profile:view"]'::jsonb
 WHERE code IN ('ops', 'auditor')
   AND NOT (permissions @> '["drilldown_profile:view"]'::jsonb);
