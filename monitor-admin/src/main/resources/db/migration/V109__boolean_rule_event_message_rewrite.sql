-- =============================================================
-- 布尔型内置规则历史告警事件文案状态化
--   V108 固化了复制线程停止 / MGR 成员异常的触发条件展示，
--   AlertMessageRenderer 已对新事件产出状态化文案；
--   本迁移把历史事件里"当前值 0，小于1，告警触发"这类数值句式
--   重写为与新文案一致的状态化描述（按是否已恢复分别取触发/恢复文案）。
-- =============================================================

-- 复制 IO 线程停止
UPDATE alert_event e
   SET alert_message = CASE WHEN e.recovery_time IS NULL
           THEN '[复制 IO 线程停止]，检测到复制 IO 线程停止（Slave_IO_Running = No），告警触发'
           ELSE '[复制 IO 线程停止]，复制 IO 线程恢复运行（Slave_IO_Running = Yes），告警恢复'
       END
 WHERE e.rule_id IN (SELECT id FROM alert_rule WHERE rule_code = 'builtin.replication.io_stopped');

-- 复制 SQL 线程停止
UPDATE alert_event e
   SET alert_message = CASE WHEN e.recovery_time IS NULL
           THEN '[复制 SQL 线程停止]，检测到复制 SQL 线程停止（Slave_SQL_Running = No），告警触发'
           ELSE '[复制 SQL 线程停止]，复制 SQL 线程恢复运行（Slave_SQL_Running = Yes），告警恢复'
       END
 WHERE e.rule_id IN (SELECT id FROM alert_rule WHERE rule_code = 'builtin.replication.sql_stopped');

-- MGR 成员状态异常
UPDATE alert_event e
   SET alert_message = CASE WHEN e.recovery_time IS NULL
           THEN '[MGR 成员状态异常]，检测到 MGR 成员状态异常（OFFLINE / ERROR），告警触发'
           ELSE '[MGR 成员状态异常]，MGR 成员状态恢复 ONLINE，告警恢复'
       END
 WHERE e.rule_id IN (SELECT id FROM alert_rule WHERE rule_code = 'builtin.gr.member_state.critical');
