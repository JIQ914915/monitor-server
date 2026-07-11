-- 事件驱动阻塞链现场快照（需求 §11.x / 差距分析 模块4）
-- 锁相关告警/场景事件建单成功后，采集端即席连接目标实例抓取阻塞链现场，
-- 结果以 JSONB 落到事件行，下钻页渲染"阻塞链现场"卡片。
-- 结构：{"capturedAt":"...","dbVersion":"8.0","total":N,"error":null,
--        "rows":[{"waitAgeSecs","lockedTable","lockedType","waitingPid","waitingQuery","blockingPid","blockingQuery"}]}
ALTER TABLE alert_event
    ADD COLUMN IF NOT EXISTS blocking_chain_snapshot JSONB;

COMMENT ON COLUMN alert_event.blocking_chain_snapshot IS
    '阻塞链现场快照（锁相关事件建单时即席抓取；capturedAt/total/error/rows[]）';
