# 实例删除关联数据清理设计

## 背景

当前 `InstanceServiceImpl.delete(Long id)` 只删除 `db_instance` 主记录。采集时序表、采集状态表、告警事件及其子记录等仍按 `instance_id` 保留，造成孤儿数据和存储浪费。

## 目标

删除实例时，永久删除该实例产生或配置的全部实例维度数据，包括采集数据、采集状态、告警事件、事件审计记录和实例级业务配置。任一清理步骤失败时，整次删除回滚，实例主记录保持不变。

不删除全局定义数据，例如指标定义、告警规则模板、场景模板、数据库类型和版本。

## 方案

在服务层事务中调用专用的实例数据清理 Mapper，再删除 `db_instance`。清理 Mapper 使用参数化 SQL，以 `instance_id` 或由该实例事件 ID 派生的条件显式删除各表数据。

不在本次改动中批量增加外键级联，也不使用数据库触发器。现有 TimescaleDB 表和历史数据使外键迁移风险较高；触发器会隐藏删除行为，不利于服务层测试和维护。

## 清理范围

清理组件覆盖当前数据库迁移中所有实例维度数据：

- 数值指标：`metric_data_1m`、`metric_data_1h`、`metric_data_1d`。
- 文本指标：`metric_text_data_1m`、`metric_text_data_1h`、`metric_text_data_1d`。
- 对象与诊断数据：`metric_top_sql`、`metric_capacity_object`、`metric_long_conn`、`metric_slow_sql_sample`，以及仍存在的历史兼容表。
- 采集运行状态：`collect_log`、`counter_snapshot`、`instance_collector`。
- 实例级配置与评估状态：`alert_rule_instance_config`、`scenario_instance_config`、`alert_evaluate_lock`、`alert_evaluate_window`。
- 实例级辅助数据：`slow_sql_optimize_mark` 及其他通过迁移确认含 `instance_id` 的业务表。
- 告警事件：目标实例的 `alert_event`，以及通过 `event_id` 关联的 `alert_notify_record`、`alert_event_operate_log`、`llm_analysis` 等子记录。

实现前将以全部 Flyway 迁移文件为准生成最终表清单，避免仅依赖实体类而漏掉无实体的时序表。

报告及计划表中的 `instance_ids` 是跨实例 JSON 数组，不直接删除整条全局记录。本次将从数组中移除目标实例 ID；若业务模型要求保留生成时快照的历史报告，则已生成报告保持不变，后续计划不再引用已删除实例。

## 删除顺序与事务

`InstanceServiceImpl.delete` 保持数据权限校验，并先确认实例存在。随后在同一 Spring 事务中执行：

1. 查询目标实例的告警事件 ID。
2. 删除通知记录、事件操作流水、LLM 分析等事件子记录。
3. 删除目标实例的告警事件。
4. 删除评估状态、实例级规则/场景配置和辅助业务数据。
5. 删除采集状态、日志及全部时序采集数据。
6. 清理报告计划等集合字段中的实例引用。
7. 删除 `db_instance` 主记录。

任何 SQL 异常向上抛出，由事务统一回滚。不吞掉异常，也不在部分清理后继续删除主记录。

## 接口与错误处理

HTTP 接口及请求结构保持不变。无权访问仍返回现有业务异常。实例不存在时返回“实例不存在”，避免对不存在 ID 执行一轮清理后静默成功。

删除操作具备数据库层面的幂等性：各关联表没有匹配记录时删除零行，不视为失败。

## 测试

按测试驱动方式实现：

- 先新增失败测试，证明当前删除只操作主表，未调用关联清理。
- 验证存在实例时先调用清理组件，再删除主记录。
- 验证实例不存在时抛出业务异常，且不执行关联清理。
- 验证清理 Mapper 的 SQL 覆盖最终确认的所有实例维度表及告警事件子表。
- 运行 `monitor-service`、`monitor-dao` 相关测试，并运行服务端 Maven 构建或项目现有完整测试命令。

## 兼容性与风险控制

删除是不可逆操作，沿用现有删除权限和前端确认交互，不新增异步任务。大实例可能有较多 TimescaleDB 历史数据；本次优先保证原子性和正确性，若生产数据量验证显示同步事务超时，再单独设计异步删除任务，不预先增加复杂度。
