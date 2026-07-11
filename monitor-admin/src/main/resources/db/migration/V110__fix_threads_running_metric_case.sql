-- =============================================================
-- 修复内置规则「活跃线程数过多」的指标名大小写不一致
--
--   V59 建规则时 metric_name 写成小写 mysql.status.threads_running，
--   而 GlobalStatusItem 落库与 V24 指标定义均为 mysql.status.Threads_running。
--   评估器按 metric_code 精确匹配查询（区分大小写），导致该规则
--   每轮评估都查不到值（metric_missing），从未触发过。
--
--   同步修正 V78 按 metric_name 原样回填的 alert_rule_metric_ref 悬空记录，
--   使扫描间隔能力（minAllowedIntervalMin）也能正确关联到指标定义。
-- =============================================================

-- 1. 修正规则的监控指标编码
UPDATE alert_rule
   SET metric_name = 'mysql.status.Threads_running',
       updated_at  = now()
 WHERE rule_code   = 'builtin.threads_running.critical'
   AND metric_name = 'mysql.status.threads_running';

-- 2. 修正规则-指标关联：改写小写悬空记录（若正确记录已存在则跳过改写，随后统一清理）
UPDATE alert_rule_metric_ref ref
   SET metric_code = 'mysql.status.Threads_running'
 WHERE ref.metric_code = 'mysql.status.threads_running'
   AND NOT EXISTS (
       SELECT 1 FROM alert_rule_metric_ref dup
        WHERE dup.rule_id = ref.rule_id
          AND dup.metric_code = 'mysql.status.Threads_running'
   );

DELETE FROM alert_rule_metric_ref
 WHERE metric_code = 'mysql.status.threads_running';
