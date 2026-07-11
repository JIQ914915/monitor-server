-- =============================================================
-- V128：告警下钻画像完善（§11.7 事件下钻 × §11.8 场景化监控）
--   1. 场景综合事件画像匹配：内置 7 个场景按 scenario_code 前缀映射到对应画像，
--      场景事件下钻不再落到「通用类」兜底（后端以 scenario_code 作为匹配键）：
--        connection_pool_exhaustion → 连接与会话类
--        sql_performance_degradation / tmp_table_pressure → SQL 性能类
--        lock_contention → 锁与事务类
--        replication_risk → 高可用/复制类
--        buffer_pool_pressure → 缓存类
--        write_amplification → 容量空间类
--   2. 按内置告警规则补齐画像内容：
--        连接类关联指标 + 活跃线程数（builtin.threads_running.critical）
--        锁类关联指标 + 锁等待超时次数（builtin.lock_timeout.warning）
--        容量类关联指标 + 主库写入TPS，原因 + 写入放大（scenario.write_amplification）
-- 注：全脚本幂等（jsonb @> 存在性守卫），仅更新内置画像，不覆盖用户自定义修改的其他字段。
-- =============================================================

-- ---- 1. 场景编码 → 画像匹配规则 ----
UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.connection_pool_exhaustion"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'connections'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.connection_pool_exhaustion"}]'::jsonb;

UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.sql_performance_degradation"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'slowsql'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.sql_performance_degradation"}]'::jsonb;

UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.tmp_table_pressure"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'slowsql'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.tmp_table_pressure"}]'::jsonb;

UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.lock_contention"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'lock'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.lock_contention"}]'::jsonb;

UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.replication_risk"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'replication'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.replication_risk"}]'::jsonb;

UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.buffer_pool_pressure"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'cache'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.buffer_pool_pressure"}]'::jsonb;

UPDATE alert_drilldown_profile
   SET match_rules = match_rules || '[{"matchType":"prefix","pattern":"scenario.write_amplification"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'capacity'
   AND NOT match_rules @> '[{"matchType":"prefix","pattern":"scenario.write_amplification"}]'::jsonb;

-- 未命中任何场景前缀的自定义场景仍回退「通用类」兜底（match_rules 为空），无需额外规则

-- ---- 2. 按内置规则补齐画像内容 ----

-- 2.1 连接与会话类：+ 活跃线程数（builtin.threads_running.critical 的触发指标）
UPDATE alert_drilldown_profile
   SET related_metrics = related_metrics
       || '[{"code":"mysql.status.Threads_running","label":"活跃线程数","unit":"","color":"#B45309"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'connections'
   AND NOT related_metrics @> '[{"code":"mysql.status.Threads_running"}]'::jsonb;

-- 2.2 锁与事务类：+ 锁等待超时次数（builtin.lock_timeout.warning 的触发指标）
UPDATE alert_drilldown_profile
   SET related_metrics = related_metrics
       || '[{"code":"mysql.innodb.lock_timeout_count","label":"锁等待超时次数","unit":"","color":"#B45309"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'lock'
   AND NOT related_metrics @> '[{"code":"mysql.innodb.lock_timeout_count"}]'::jsonb;

-- 2.3 容量空间类：+ 主库写入TPS（写入放大场景的直接信号）
UPDATE alert_drilldown_profile
   SET related_metrics = related_metrics
       || '[{"code":"mysql.tps","label":"主库写入TPS","unit":"","color":"#E08600"}]'::jsonb,
       updated_at = now()
 WHERE profile_code = 'capacity'
   AND NOT related_metrics @> '[{"code":"mysql.tps"}]'::jsonb;

-- 2.4 容量空间类：+ 写入放大原因（scenario.write_amplification 联动）
UPDATE alert_drilldown_profile
   SET causes = causes || $J$[
         {"cause":"写入放大导致 Binlog 暴增","confidence":0.65,"color":"warning",
          "evidence":["TPS 与 Binlog 占用同步陡增时，多为批量写入、大事务或无差别更新（如全表 UPDATE）",
                      "检查是否有定时批处理任务在告警时段执行",
                      "binlog_format=ROW 时大范围更新会成倍放大 Binlog 写入量"]}
       ]$J$::jsonb,
       updated_at = now()
 WHERE profile_code = 'capacity'
   AND NOT causes @> '[{"cause":"写入放大导致 Binlog 暴增"}]'::jsonb;
