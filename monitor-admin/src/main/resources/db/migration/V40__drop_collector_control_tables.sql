-- =============================================================
-- 清理采集控制表（V27 创建）
--
-- 背景：原设计在 collector_config / instance_collector / capability_matrix
--       三张表实现指标级采集控制（按采集器启停、版本矩阵、前端能力门控）。
--       实际落地后决策简化：采集控制粒度只到实例层（status=paused 跳过），
--       不做指标级细化，相关 Java 代码已同步删除。
--
--   collector_config   ——  采集器配置（默认启用、超时、权限声明）
--   instance_collector ——  实例-采集器关联（实例级开关、运行状态）
--   capability_matrix  ——  能力矩阵（前端菜单可用性判定）
-- =============================================================

DROP TABLE IF EXISTS instance_collector;
DROP TABLE IF EXISTS capability_matrix;
DROP TABLE IF EXISTS collector_config;
