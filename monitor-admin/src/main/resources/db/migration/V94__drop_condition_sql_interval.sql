-- =============================================================
-- V94：清理 condition_config 中的遗留键 sqlInterval（秒级执行间隔）
--   自定义规则的评估频率已统一由 scan_interval_min（分钟）承载：
--   保存时校验不小于指标采集频率推导的最小允许间隔（minAllowedIntervalMin），
--   评估侧 shouldRunNow 只读 scan_interval_min。
--   sqlInterval 的存量值已在 V78 折算进 scan_interval_min（CEIL(秒/60)），
--   该键自此无任何消费方，属死配置，清除以免误导。
-- =============================================================

UPDATE alert_rule_instance_config
   SET condition_config = condition_config - 'sqlInterval'
 WHERE condition_config ? 'sqlInterval';

UPDATE alert_rule
   SET condition_config = condition_config - 'sqlInterval'
 WHERE condition_config ? 'sqlInterval';
