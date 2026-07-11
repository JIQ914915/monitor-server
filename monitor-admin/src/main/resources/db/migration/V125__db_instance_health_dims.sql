-- =============================================================
-- 首页整体健康总览（§11.1.2）：为 db_instance 增加五维健康得分快照。
--   由健康评分作业（healthCalculateJobHandler）随总分一并写入，
--   供首页"整体健康总览"聚合五维达标率时直接读取，避免实时全量重算。
--   结构：{"availability":98,"performance":85,"stability":90,"capacity":75,"security":100}
--   维度得分 -1 表示该维度当前无可用数据（聚合时跳过）。
-- =============================================================

ALTER TABLE db_instance ADD COLUMN IF NOT EXISTS health_dims JSONB;

COMMENT ON COLUMN db_instance.health_dims IS
  '五维健康得分快照（availability/performance/stability/capacity/security，0-100，-1=该维度无数据），健康评分作业写入';
