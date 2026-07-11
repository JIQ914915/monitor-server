-- =============================================================
-- V123: 状态枚举字典化（补齐硬编码状态值）
--   notify_status            通知记录状态（alert_notify_record.status）
--   scenario_status          场景运行状态（ScenarioVo.currentStatus）
--   health_level             健康等级（健康分等级 + 机群分布）
--   notify_channel_type      通知渠道类型（notify_channel.channel）
--   alert_rule_type          告警规则来源（alert_rule.rule_type）
--   alert_event_operate_type 告警事件操作类型（alert_event_operate_log.operate_type）
-- 幂等：类型按唯一键 ON CONFLICT 跳过；字典项按 (dict_type, item_value) NOT EXISTS 防重。
-- =============================================================

INSERT INTO sys_dict_type (dict_type, dict_name, remark) VALUES
  ('notify_status',            '通知状态',      '告警通知记录发送状态'),
  ('scenario_status',          '场景状态',      '监控场景当前运行状态'),
  ('health_level',             '健康等级',      '实例健康分等级'),
  ('notify_channel_type',      '通知渠道类型',  '告警通知发送渠道'),
  ('alert_rule_type',          '规则来源',      '告警规则来源：内置模板/用户自定义'),
  ('alert_event_operate_type', '事件操作类型',  '告警事件处置流水的操作动作')
ON CONFLICT (dict_type) DO NOTHING;

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT v.dict_type, v.item_value, v.item_label, v.tag_type, v.sort
FROM (VALUES
  -- 通知记录状态
  ('notify_status', 'pending', '待发送', 'info',    1),
  ('notify_status', 'sending', '发送中', 'primary', 2),
  ('notify_status', 'success', '成功',   'success', 3),
  ('notify_status', 'failed',  '失败',   'warning', 4),
  ('notify_status', 'dead',    '死信',   'danger',  5),
  -- 场景运行状态
  ('scenario_status', 'triggered', '触发中',   'danger',  1),
  ('scenario_status', 'normal',    '正常',     'success', 2),
  ('scenario_status', 'unknown',   '数据缺失', 'warning', 3),
  ('scenario_status', 'disabled',  '已停用',   'info',    4),
  -- 健康等级
  ('health_level', 'excellent', '优秀',     'success', 1),
  ('health_level', 'good',      '良好',     'primary', 2),
  ('health_level', 'warning',   '警告',     'warning', 3),
  ('health_level', 'critical',  '严重',     'danger',  4),
  ('health_level', 'offline',   '离线',     'info',    5),
  ('health_level', 'no_data',   '暂无数据', 'info',    6),
  -- 通知渠道类型
  ('notify_channel_type', 'webhook',  'Webhook',  'primary', 1),
  ('notify_channel_type', 'dingtalk', '钉钉',     'primary', 2),
  ('notify_channel_type', 'feishu',   '飞书',     'primary', 3),
  ('notify_channel_type', 'wecom',    '企业微信', 'primary', 4),
  ('notify_channel_type', 'email',    '邮件',     'info',    5),
  ('notify_channel_type', 'sms',      '短信',     'info',    6),
  -- 规则来源
  ('alert_rule_type', 'builtin', '内置',   'info',    1),
  ('alert_rule_type', 'custom',  '自定义', 'success', 2),
  -- 事件操作类型
  ('alert_event_operate_type', 'confirm',  '确认', 'primary', 1),
  ('alert_event_operate_type', 'handling', '受理', 'warning', 2),
  ('alert_event_operate_type', 'silence',  '静默', 'info',    3),
  ('alert_event_operate_type', 'close',    '关闭', 'info',    4)
) AS v(dict_type, item_value, item_label, tag_type, sort)
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item d
   WHERE d.dict_type = v.dict_type AND d.item_value = v.item_value);
