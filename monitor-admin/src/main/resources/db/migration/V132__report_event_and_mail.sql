-- =============================================================
-- V132：报告中心增强（§11.7 事件报告归档 + §11.9 定时报告邮件推送）
--   1. 字典 report_type 新增 event（事件报告）：告警下钻页生成的
--      诊断报告改为后端生成并归档，含处理记录/恢复情况/预防建议
--   2. report_schedule 新增 notify_emails：定时报告生成后推送邮件
-- 注：全脚本幂等
-- =============================================================

INSERT INTO sys_dict_item (dict_type, item_value, item_label, tag_type, sort)
SELECT 'report_type', 'event', '事件报告', 'danger', 4
WHERE NOT EXISTS (
  SELECT 1 FROM sys_dict_item WHERE dict_type = 'report_type' AND item_value = 'event'
);

ALTER TABLE report_schedule
  ADD COLUMN IF NOT EXISTS notify_emails JSONB NOT NULL DEFAULT '[]'::jsonb;
COMMENT ON COLUMN report_schedule.notify_emails IS '报告生成后推送的收件邮箱列表（空数组不推送；依赖 spring.mail 配置）';
