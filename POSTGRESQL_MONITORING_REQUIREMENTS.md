# PostgreSQL 监控能力建设需求文档

| 属性 | 内容 |
| --- | --- |
| 文档版本 | v1.0 |
| 编制日期 | 2026-07-14 |
| 适用范围 | monitor-server、monitor-web、monitor-collector-postgresql |
| 文档状态 | 需求基线，待评审 |
| 建设主题 | PostgreSQL 监控从“指标可见”升级为“诊断、建议、处置、验证”闭环 |

## 1. 背景

当前平台已经具备 PostgreSQL 基础监控和部分深度监控能力，覆盖可用性、连接、事务、锁、等待事件、WAL、复制、复制槽、表膨胀、XID、Vacuum、表热点、Top SQL、慢 SQL、参数和基础安全检查。

现阶段的主要问题已经不是“缺少基础曲线”，而是：

1. PostgreSQL 17、18 尚未正式适配，部分系统视图在新版本中已经发生不兼容变化。
2. 锁、慢 SQL、Vacuum、复制等异常能够告警，但缺少完整现场和根因链路。
3. SQL 分析以排行榜和单次 EXPLAIN 为主，缺少历史对比、性能回退和执行计划变化分析。
4. Vacuum、索引、安全等能力以事实展示为主，尚未形成工作负载感知的顾问能力。
5. 日志、逻辑复制、WAL 归档和恢复演练等生产运维能力尚不完整。
6. 对大规模实例的统一配置、自动发现、容量预测和异常关联能力仍需建设。

因此，下一阶段建设目标是让平台能够回答以下问题：

- 当前发生了什么？
- 具体是谁造成的，影响了哪些对象和会话？
- 为什么发生，和 SQL、配置、主机资源或复制状态有什么关系？
- 应该如何处理，处理动作有什么风险？
- 处理后问题是否真正恢复？

## 2. 分析依据

### 2.1 PostgreSQL 官方能力

本需求主要参考以下官方能力：

- 累积统计与活动监控：`pg_stat_activity`、`pg_stat_database`、`pg_stat_io`、`pg_stat_wal`、`pg_stat_checkpointer` 等。
- SQL 统计：`pg_stat_statements` 的规划时间、执行时间、I/O、WAL、JIT、统计重置和淘汰信息。
- 进度视图：VACUUM、ANALYZE、CREATE INDEX、REINDEX、CLUSTER、COPY、BASE_BACKUP。
- 复制监控：物理复制、WAL receiver、恢复冲突、逻辑订阅和订阅冲突。
- 辅助扩展：`pgstattuple`、`amcheck`。
- PostgreSQL 版本支持策略和安全版本信息。

### 2.2 对标产品

| 产品 | 重点参考能力 |
| --- | --- |
| Prometheus postgres_exporter | 标准化指标、版本兼容、可选择采集器、最小权限账号 |
| Percona PMM | Query Analytics、执行计划、Advisor、告警、备份恢复 |
| pgwatch | PostgreSQL 指标模板、可配置采集频率、扩展性 |
| pganalyze | SQL 性能历史、执行计划、Schema Statistics、Vacuum Advisor、索引建议 |
| EDB PEM | 多实例统一管理、探针/Profile、日志、SQL Profiling、HA、备份、专家检查 |
| SolarWinds DPA | 基于等待时间的 SQL 性能归因和工作负载分析 |
| Datadog DBM | 数据库、主机、日志、APM Trace 和部署事件关联 |

## 3. 当前能力基线

### 3.1 已具备能力

- PostgreSQL 13～16 基础版本路由。
- 可用性、运行时间、连接总量和连接使用率。
- TPS、提交、回滚、缓存命中、临时文件、死锁和行操作速率。
- 锁等待数量、被阻塞会话数量、长事务和 idle in transaction。
- 等待事件大类和 Top 等待事件。
- checkpoint、bgwriter、WAL、归档状态。
- 主从角色、复制延迟、逐从库延迟、复制槽积压。
- PG 16+ `pg_stat_io` 基础读写指标。
- 表膨胀估算、XID 回卷风险、Vacuum 活动。
- 表读写热点、未使用索引、失效索引。
- `pg_stat_statements` Top SQL 和 `pg_stat_activity` 慢 SQL 采样。
- 实时 EXPLAIN、SQL 趋势、慢 SQL 详情和 AI 分析入口。
- 参数快照、扩展状态、角色、SSL 和基础安全信息。
- PostgreSQL 独立实时、性能、复制、对象、慢 SQL、配置、场景、告警页面。

### 3.2 已知缺口

- PostgreSQL 17 起 checkpoint 统计迁移到 `pg_stat_checkpointer`，现有采集 SQL 不兼容。
- PostgreSQL 18 新增按后端 I/O/WAL 和字节级 I/O，现有模型未覆盖。
- 当前阻塞链事件快照仅实现 MySQL，PostgreSQL 告警没有真实阻塞链现场。
- 数据库级和对象级采集主要面向实例配置的连接库，没有形成实例内多数据库发现和覆盖状态。
- Top SQL 为小时级差值，未充分使用 planning、I/O timing、WAL、JIT、波动等字段。
- 表膨胀主要根据统计估算，尚未形成按表 Vacuum 效率和参数建议。
- 尚未系统接入 PostgreSQL 逻辑复制。

## 4. 建设目标

### 4.1 产品目标

1. 支持当前仍由 PostgreSQL 官方维护的主版本，并能识别版本能力差异。
2. 对锁、慢 SQL、Vacuum、复制等高频故障保留可复盘的事件现场。
3. 将 SQL 性能分析从“Top 排名”升级为“性能变化、根因和计划回退分析”。
4. 建立 Vacuum、索引、配置和安全顾问能力，输出证据、建议和风险说明。
5. 覆盖日志、复制、WAL 归档和恢复演练等生产运维场景。
6. 支持大规模实例的统一策略、容量预测、异常关联和合规治理。

### 4.2 成功标准

- PostgreSQL 14～18 的核心采集项通过对应版本集成测试。
- 锁告警能够展示告警触发时的完整阻塞树和根阻塞会话。
- 对 SQL 变慢能够区分调用量增长、单次执行变慢、I/O 增长、锁等待和执行计划变化。
- Vacuum Advisor 能输出表级证据和明确建议，而不是仅展示 dead tuple 数量。
- 复制、备份、日志异常能够形成告警、下钻和处理建议闭环。
- 所有高风险操作必须经过权限校验、二次确认并产生审计记录。

### 4.3 非目标

- 不在本轮建设数据库代理、连接池或 SQL 网关。
- 不管理外部 HA 和备份组件，不保存备份文件。
- 不默认自动终止会话、自动改参数或自动创建/删除索引。
- 不执行未经人工确认的 `EXPLAIN ANALYZE`、VACUUM FULL、REINDEX 或主从切换。
- 不为了统一多数据库实现而削弱现有 MySQL 功能。

## 5. 总体设计原则

### 5.1 能力探测优先于版本硬编码

版本决定默认适配器，实际执行前通过系统视图、字段、扩展和权限探测确认能力。探测结果保存为实例能力矩阵，并在前端解释“无数据”的真实原因。

### 5.2 区分指标层级

- 实例级：连接、WAL、checkpoint、复制、后台进程。
- 数据库级：事务、缓存、临时文件、数据库容量、冲突。
- 对象级：表、索引、分区、函数、序列、表空间。
- 会话级：PID、用户、应用、客户端、SQL、等待、阻塞关系。

页面、接口和存储必须携带对应层级标识，不能把“连接库数据”误展示为“全实例数据”。

### 5.3 低侵入和最小权限

- 默认只读采集，使用 `pg_monitor` 等官方角色满足大部分监控场景。
- 对 `pg_stat_statements`、`pgstattuple`、`amcheck` 分别声明扩展和权限要求。
- 对高开销采集设置超时、行数限制、频率和能力开关。
- SQL 文本、参数、日志和运维事件必须支持脱敏和访问控制。

### 5.4 建议必须可解释

每条顾问建议至少包含：

- 问题对象。
- 观察窗口。
- 指标和事实证据。
- 判断规则或阈值来源。
- 建议动作。
- 动作风险。
- 验证方法。

## 6. 分期路线图

| 阶段 | 主题 | 核心结果 | 前置依赖 |
| --- | --- | --- | --- |
| 第一期 | 版本兼容与实时现场 | PG 14～18 可监控；锁和会话问题可现场定位 | 现有 PG 采集链路 |
| 第二期 | SQL、Vacuum 与索引深度诊断 | 性能变化可解释；形成首批顾问能力 | 第一期能力矩阵和对象标识 |
| 第三期 | 日志、复制、HA 与备份运维 | 覆盖生产运维主链路 | 前两期告警和下钻框架 |
| 第四期 | 平台化与智能治理 | 支持规模化治理、预测和跨域关联 | 稳定的历史数据和运维数据 |

工期由人员配置和并行策略决定，本需求不预设固定自然日。每一期应独立上线、独立验收，不与后续阶段强绑定。

## 7. 第一期：版本兼容与实时现场

### 7.1 阶段目标

- 完成 PostgreSQL 14～18 正式兼容。
- 明确 PostgreSQL 13 已 EOL，并提供风险提示而非继续作为默认支持版本。
- 建立实例能力矩阵和多数据库发现基础。
- 实现实时会话、阻塞树和告警现场快照。

### 7.2 功能范围

#### PG-P1-01 版本适配

- 增加 PG 17、PG 18 适配器。
- PG 13～16 可继续复用兼容基线，但支持列表调整为 14～18。
- PG 17 checkpoint 从 `pg_stat_checkpointer` 采集。
- PG 17 适配 `pg_stat_bgwriter`、`pg_stat_progress_vacuum` 等字段变化。
- PG 18 适配 `pg_stat_io` 字节指标、WAL I/O、按后端 I/O/WAL 能力。
- 页面展示主版本、完整小版本、支持状态、EOL 日期和是否落后于安全小版本。

#### PG-P1-02 实例能力矩阵

至少探测：

- `pg_stat_statements` 是否加载、创建、可读。
- `pg_stat_io`、`pg_stat_checkpointer`、各类 progress view 是否存在。
- `pgstattuple`、`amcheck` 是否可用。
- 当前账号是否具有查看活动、SQL 文本、复制、角色等权限。
- `track_io_timing`、`track_wal_io_timing`、`compute_query_id` 等关键开关状态。

能力结果应区分：支持且可用、支持但未配置、权限不足、版本不支持、探测失败。

#### PG-P1-03 多数据库发现基础

- 自动发现实例内允许连接的非模板数据库。
- 展示数据库名称、容量、连接权限、是否允许连接、是否已纳入对象级采集。
- 配置对象级采集范围：仅监控库、指定数据库、全部可连接数据库。
- 限制单实例数据库并发和总采集时长，避免数据库数量多时拖垮分钟任务。
- 本期先完成发现、范围配置和覆盖状态；完整对象级跨库指标可随第二期扩展。

#### PG-P1-04 实时会话

会话列表至少包含：

- PID、数据库、用户、应用名、客户端地址。
- backend type、state、事务开始时间、查询开始时间、状态变化时间。
- wait event type、wait event、query id、SQL 摘要。
- 是否被阻塞、阻塞者 PID、是否为根阻塞者。

支持按数据库、用户、应用、状态、等待类型和持续时间过滤。

#### PG-P1-05 PostgreSQL 阻塞树

- 使用 `pg_blocking_pids()`、`pg_stat_activity`、`pg_locks` 和对象目录还原阻塞关系。
- 支持多层阻塞链和一个会话阻塞多个下游的树形结构。
- 标识根阻塞者、等待时长、锁模式、锁对象和影响会话数量。
- 对锁相关告警异步抓取现场，保存到告警事件中。
- 快照失败不能影响告警主流程，并应返回权限不足、连接失败或现场已消失等明确原因。

#### PG-P1-06 会话处置

- 提供复制 PID、复制 SQL、取消查询、终止会话入口。
- `pg_cancel_backend` 和 `pg_terminate_backend` 默认关闭，通过权限点单独授权。
- 操作前展示数据库、用户、应用、客户端、事务时长和影响提示。
- 终止会话必须二次确认并填写原因。
- 记录操作者、时间、目标 PID、操作类型、原因和结果；禁止记录密码等敏感信息。

### 7.3 页面交付

- PG 实时监控页增加“会话”和“阻塞树”区域。
- 实例详情增加“PG 能力检测”和“数据库覆盖范围”。
- 告警下钻增加“阻塞链现场”卡片。
- 系统版本页面增加 EOL 和安全小版本状态。

### 7.4 后端交付

- PG 17/18 Adapter 和兼容测试。
- 能力探测服务及持久化模型。
- 多数据库发现、采集范围配置接口。
- 实时会话、阻塞树查询接口。
- PG 阻塞链告警快照实现。
- 会话操作权限、审计和安全校验。

### 7.5 验收标准

- PG 14、15、16、17、18 均可完成连接测试和核心指标采集。
- PG 17 不再因查询旧 `pg_stat_bgwriter` 字段导致采集项失败。
- PG 18 I/O 页面能展示字节级读写；低版本明确显示版本不支持。
- 构造三层阻塞链后，页面顺序、根节点、SQL 和锁对象正确。
- 锁告警触发后，即使阻塞随后消失，告警详情仍能查看触发时快照。
- 权限不足时页面给出最小授权建议，不展示模糊的“暂无数据”。
- 未授权用户无法调用 cancel/terminate 接口。

### 7.6 本期不包含

- 自动终止根阻塞会话。
- 自动修改数据库参数。
- 文件型 PostgreSQL 日志采集。
- 全量对象级跨库深度分析。

## 8. 第二期：SQL、Vacuum 与索引深度诊断

### 8.1 阶段目标

- 将 Top SQL 从排行榜升级为历史 Query Analytics。
- 建立执行计划历史和性能回退识别。
- 建立 Vacuum Advisor 和 Index Advisor 的首个可用版本。
- 扩展数据库、表、索引、分区和表空间的对象级分析。

### 8.2 功能范围

#### PG-P2-01 Query Analytics 2.0

在现有调用次数、执行时间、行数和块读取基础上增加：

- 规划次数、总/平均规划时间。
- 最小、最大、平均和标准差执行时间。
- shared/local/temp block 命中、读取、写入和脏块。
- I/O 读写时间。
- WAL records、FPI 和 WAL bytes。
- JIT functions、generation、inlining、optimization 和 emission 时间。
- 首次发现时间、最近出现时间、统计窗口和统计重置时间。
- `pg_stat_statements_info.dealloc` 和 stats reset 监控。

支持按照数据库、用户、query id、时间范围和指标排序。能够区分：

- 调用量增长。
- 单次执行时间增长。
- 规划时间增长。
- I/O 或临时文件增长。
- WAL 放大。
- 执行时间离散度增大。

#### PG-P2-02 SQL 性能回退检测

- 为 query id 建立工作日/时段基线。
- 比较当前窗口与历史基线的调用次数、平均耗时、总耗时、I/O、临时写和 WAL。
- 识别新 SQL、突增 SQL、变慢 SQL和消失后重新出现的 SQL。
- 回退事件必须记录比较窗口、基线值、当前值和变化比例。
- 与锁、主机 CPU/I/O、配置变化和发布事件预留关联字段。

#### PG-P2-03 执行计划历史

- 默认只执行安全的 `EXPLAIN (FORMAT JSON)`，不执行 `ANALYZE`。
- 保存 query id、数据库、采集时间、SQL 哈希、计划 JSON 和关键节点摘要。
- 对同一 query id 的计划进行版本化，识别计划哈希变化。
- 展示节点树、成本、估算行数、扫描方式、Join 方式、过滤条件和索引。
- 对用户主动提交的 `EXPLAIN ANALYZE` 保留独立高风险权限和明确执行风险；首版可不开放。
- 对无法安全复现的慢 SQL 不自动执行或解析外部计划，仅保留手工安全 EXPLAIN。

#### PG-P2-04 Vacuum Advisor

按表持续采集和分析：

- live/dead tuple、修改量、插入量和 dead tuple 产生速度。
- last vacuum、last autovacuum、last analyze、last autoanalyze。
- vacuum/analyze 次数、当前进度、持续时间和阻塞水位。
- relfrozenxid、relminmxid 及回卷风险。
- 表级 autovacuum 参数和实例级默认参数。
- 大表、分区父表和高更新表的特殊风险。

输出建议示例：

- 降低某表 `autovacuum_vacuum_scale_factor`。
- 调整 threshold、cost limit 或 worker 数量。
- 对分区父表补充定期 ANALYZE。
- 处理阻塞 Vacuum 的长事务或复制槽。
- 使用 `pgstattuple` 对高风险对象执行低频精确检查。

#### PG-P2-05 Index Advisor

- 保留现有未使用索引和失效索引能力。
- 增加重复索引、左前缀重叠索引和相同列集合索引识别。
- 统计索引容量、扫描次数、读取元组和写入维护成本。
- 结合高耗时 SQL 的过滤、Join 和排序列形成缺失索引候选。
- 建议必须标记“候选”，不得直接执行创建或删除。
- 对主键、唯一约束、外键支撑索引和复制标识索引进行保护。
- 可选低频使用 `amcheck` 验证高风险索引一致性。

#### PG-P2-06 对象与容量分析增强

- 数据库、Schema、表空间、表、索引、TOAST 和分区容量。
- 表和索引增长趋势。
- 顺序扫描热点、低命中表、临时对象和函数统计。
- 多数据库采集范围内的对象统一检索。
- 表空间容量需要和主机磁盘建立可解释映射；无法映射时明确提示。

### 8.3 页面交付

- Query Analytics 页面和 SQL 回退列表。
- JSON 执行计划树及计划版本对比。
- Vacuum Advisor 页面。
- Index Advisor 页面。
- 对象页增加容量、分区、表空间和增长分析。

### 8.4 验收标准

- 同一 SQL 调用量不变但平均耗时显著上升时，能够产生回退事件。
- `pg_stat_statements` 重置后不会将累积计数回落错误计算为异常增量。
- 执行计划变化后能生成新计划版本，并突出变化节点。
- Vacuum Advisor 的每条建议均包含表、观察窗口、证据、动作和风险。
- 重复索引分析不会建议删除主键、唯一约束或复制标识所需索引。
- 对未启用 `track_io_timing` 的实例不输出误导性的零 I/O 耗时结论。

### 8.5 本期不包含

- 自动创建或删除索引。
- 自动执行 VACUUM FULL、REINDEX 或 pg_repack。
- 默认执行生产 SQL 的 EXPLAIN ANALYZE。
- 基于自然语言自动改写 SQL 并直接上线。

## 9. 第三期：复制、归档恢复与运维任务

### 9.1 阶段目标

- 覆盖 PostgreSQL 复制、WAL 归档和任务进度等系统视图可直接获取的运维证据。
- 形成不依赖第三方组件的统一运维视图和事件时间线。
- 保留人工恢复演练记录，明确区分“归档正常”和“已验证可恢复”。

### 9.2 功能范围

#### PG-P3-01 逻辑复制

- publication、subscription、subscription worker 和表同步状态。
- received/latest-end LSN、apply 延迟、同步阶段和 worker 状态。
- pg_stat_subscription_stats 冲突类型和累计数量。
- 逻辑复制槽 retained WAL、inactive 状态和 catalog xmin 风险。
- 订阅停止、worker 缺失、冲突增长和槽积压告警。

#### PG-P3-02 物理复制增强

- WAL receiver、恢复 LSN、接收/回放延迟、Timeline。
- pg_stat_database_conflicts 中的 snapshot、lock、buffer pin、deadlock 等冲突。
- pg_stat_recovery_prefetch 效率。
- 同步复制状态、候选同步节点和 quorum 配置展示。
- 主从角色变化、Timeline 变化和复制中断事件。

#### PG-P3-03 WAL 归档与恢复演练

- 基于 pg_stat_archiver 展示归档成功/失败次数、最近成功/失败 WAL 和发生时间。
- 对归档失败增长、长时间无成功归档等风险给出状态化结论。
- 支持登记恢复演练，记录备份标识、目标时间、恢复耗时、校验结果和负责人。
- 未经过恢复验证的记录必须标记为“未验证”。
- 平台不读取、保存或管理第三方备份仓库和备份文件。

#### PG-P3-04 运维任务进度中心

- VACUUM、ANALYZE、CREATE INDEX、REINDEX、CLUSTER、COPY 和 BASE_BACKUP 进度。
- 展示阶段、完成比例、已处理对象、持续时间和等待 PID。
- 对长期无进展任务告警，并关联阻塞会话。

### 9.3 页面交付

- PostgreSQL 原生运维事件时间线。
- 复制拓扑与逻辑复制页面。
- WAL 归档与恢复演练页面。
- 运维任务进度中心。
- 统一事件时间线。

### 9.4 验收标准

- 逻辑订阅停止或冲突增长时产生告警并展示订阅、worker 和冲突类型。
- WAL 归档失败增长或长期无成功归档时能够提示风险。
- 恢复演练结果可以追溯，且不会把“归档正常”错误等同于“可恢复”。
- 长时间 CREATE INDEX 等任务能展示阶段并关联其等待会话。

### 9.5 明确移除

- 不接入 PostgreSQL 文件日志、pgaudit 审计日志或其他文件型日志源。
- 不接入 auto_explain 自动日志计划。
- 不接入 Patroni REST API 或 HA 集群管理。
- 不接入 pgBackRest、Barman 等外部备份状态文件或仓库。
- 不自动恢复生产数据库。

## 10. 第四期：平台化与智能治理

### 10.1 阶段目标

- 降低大规模 PostgreSQL 实例的配置和维护成本。
- 利用长期历史数据提供预测、关联和合规治理。
- 在严格权限和审计前提下形成运维闭环。

### 10.2 功能范围

#### PG-P4-01 监控 Profile

- 将采集项、频率、阈值、告警规则、能力要求组成可复用 Profile。
- Profile 可按环境、业务等级、PG 版本、部署类型分组。
- 支持草稿、发布、回滚、批量分配和差异预览。
- 实例偏离 Profile 时生成配置漂移事件。

#### PG-P4-02 自定义安全探针

- 用户可配置只读 SQL 探针、列映射、采集频率、超时和行数上限。
- SQL 必须经过只读和单语句校验。
- 禁止访问敏感系统对象或输出凭证类字段。
- 支持探针模板导入导出和版本管理。

#### PG-P4-03 自动基线与异常检测

- 对 TPS、连接、等待、WAL、延迟、SQL、容量等建立按时段基线。
- 识别突增、突降、趋势漂移和周期性异常。
- 告警需展示当前值、预期范围、偏离程度和同时间相关事件。
- 对学习不足、数据缺失和统计重置进行置信度降级。

#### PG-P4-04 跨域关联分析

统一关联：

- PostgreSQL 指标。
- 主机 CPU、内存、磁盘和网络。
- SQL 指纹和执行计划。
- PostgreSQL 原生运维事件。
- 告警、发布、配置变更、主从切换和备份事件。

输出事件时间线和候选根因，不将时间相关性直接表述为确定因果。

#### PG-P4-05 容量预测

- 数据库、表空间、表、索引、TOAST 和 WAL 增长预测。
- 输出预计达到阈值的时间、置信区间和主要增长对象。
- 数据不足或增长不稳定时不输出伪精确日期。
- 支持业务增长情景模拟和扩容建议。

#### PG-P4-06 安全与合规中心

- PostgreSQL 版本 EOL、低于安全小版本和已知 CVE 风险。
- 角色、继承关系、超级用户、登录权限、密码有效期和危险授权。
- PUBLIC Schema/对象权限、默认权限、RLS、危险扩展和配置。
- SSL 使用情况、最小权限覆盖率和安全配置漂移。
- 支持生成检查报告和整改跟踪，不自动修改权限。

#### PG-P4-07 云数据库适配

- AWS RDS/Aurora PostgreSQL、Cloud SQL for PostgreSQL、Azure Database for PostgreSQL 能力识别。
- 区分不可访问系统视图、受限权限和云厂商替代指标。
- 接入存储自动扩展、云故障切换、参数组和维护事件。
- 云 API 凭证必须通过平台密钥管理能力引用，不允许明文保存或展示。

#### PG-P4-08 运维动作闭环

- 对取消会话、参数变更、ANALYZE、VACUUM、REINDEX 和切换等动作建立统一审批模型。
- 每个动作声明前置检查、权限、风险、执行语句/接口、超时和回滚建议。
- 动作完成后自动验证相关指标、告警和对象状态。
- 默认仅开放低风险只读动作；高风险动作按能力逐项评审开放。

### 10.3 验收标准

- Profile 变更可预览影响实例，并能回滚到上一发布版本。
- 自定义探针无法保存写 SQL、多语句 SQL 或无限制大结果集。
- 异常检测能识别周期性业务高峰，避免将固定高峰重复误报为异常。
- 容量预测明确展示数据窗口和置信度。
- 安全报告能区分事实风险、建议项和因权限不足无法验证项。
- 所有运维动作具有权限、确认、审计和结果验证记录。

## 11. 通用数据要求

### 11.1 数据保留

- 实时会话只保留短期采样，事件快照按告警保留策略保存。
- Query Analytics 保存聚合结果，原始 SQL 文本按权限和保留期控制。
- 日志和运维事件分别设置保留策略，关键事件应满足追溯周期要求。
- 执行计划按 query id 和计划哈希去重。
- 容量、版本、配置和拓扑数据需要保留长期变化历史。

### 11.2 数据重置和缺失处理

- 所有累积计数必须识别实例重启、统计 reset、扩展重建和计数回绕。
- 首次采集只建基线，不生成错误增量。
- 数据缺失必须区分版本不支持、未配置、权限不足、采集失败和确实为零。
- 顾问和异常检测不得用不完整数据生成高置信度结论。

### 11.3 敏感数据

- SQL 文本、绑定参数、日志、客户端地址和运维事件属于受限数据。
- 不采集和展示密码、Token、Cookie、连接串密码或认证头。
- 支持 SQL 常量脱敏、字段截断、按角色控制原文查看和导出。
- 操作日志不得记录目标实例密码或完整敏感 SQL 参数。

## 12. 通用权限要求

建议拆分权限点：

- 查看 PG 基础监控。
- 查看实时会话。
- 查看 SQL 原文。
- 查看日志。
- 查看运维事件。
- 查看安全报告。
- 执行 cancel backend。
- 执行 terminate backend。
- 执行诊断 SQL。
- 管理 Profile 和自定义探针。
- 管理恢复演练和监控 Profile。
- 执行高风险运维动作。

所有接口继续应用现有实例数据范围，用户只能查看和操作授权实例。

## 13. 非功能要求

### 13.1 性能

- 分钟级核心采集不能因单个深度采集项失败而整体失败。
- 深度 SQL、对象、日志和顾问任务与分钟级核心采集隔离。
- 所有目标库 SQL设置超时、最大行数和可配置频率。
- 多数据库采集必须有实例级并发和总时间预算。

### 13.2 可用性

- 云 API 错误与目标库采集错误分别记录。
- 告警现场抓取使用有界队列，不能阻塞告警评估。

### 13.3 可测试性

- 版本差异 SQL必须有解析/契约测试。
- PG 14～18 建立容器化集成测试矩阵。
- 累积计数差值测试覆盖重启、reset、回绕和首次采样。
- 阻塞树测试覆盖单链、多层树、多根和现场消失。
- 权限测试覆盖只读、pg_monitor、受限账号和高风险操作权限。

### 13.4 可观测性

- 每个采集项记录耗时、成功/失败、返回行数和错误类型，不记录敏感数据。
- 提供能力覆盖率、数据库覆盖率和采集新鲜度指标。
- 深度采集超时和被限流应有独立平台告警。

## 14. 风险与约束

| 风险 | 说明 | 控制措施 |
| --- | --- | --- |
| 目标库压力 | 对象、SQL、日志和扩展查询可能产生额外开销 | 分级频率、超时、行数限制、低峰执行、能力开关 |
| 权限不足 | 云数据库和普通监控账号无法查看全部系统信息 | 能力矩阵、最小授权文档、缺失原因明确展示 |
| SQL 泄露 | Query Analytics 和日志包含业务 SQL | 脱敏、RBAC、导出控制、保留期 |
| 错误建议 | 仅靠统计数据可能误判索引或参数 | 展示证据和置信度，仅输出候选建议，人工确认 |
| 高风险操作 | terminate、REINDEX、切换可能影响业务 | 独立权限、二次确认、审批、审计、验证 |
| 多版本差异 | 系统视图和字段随 PG 版本变化 | Adapter + capability probe + 版本集成测试 |
| 跨库规模 | 单实例数据库过多可能超过采集周期 | 范围配置、并发限制、时间预算、错峰采集 |

## 15. 里程碑完成定义

每一期只有同时满足以下条件才视为完成：

1. 需求范围内的后端、采集器、前端和数据库迁移均已交付。
2. 单元测试、契约测试和对应版本集成测试通过。
3. 页面能够解释无数据、权限不足和版本不支持。
4. 新增指标、告警、权限和保留策略有初始化/迁移数据。
5. 敏感信息检查通过，日志和接口不暴露凭证。
6. 高风险操作完成权限、确认和审计检查。
7. 运维部署、最小权限和扩展启用方式已写入文档。
8. 每一期能够独立上线和回滚，不依赖未完成的下一期功能。

## 16. 参考资料

### PostgreSQL 官方文档

- Versioning Policy：https://www.postgresql.org/support/versioning/
- Monitoring Database Activity：https://www.postgresql.org/docs/current/monitoring.html
- Cumulative Statistics System：https://www.postgresql.org/docs/current/monitoring-stats.html
- Progress Reporting：https://www.postgresql.org/docs/current/progress-reporting.html
- pg_stat_statements：https://www.postgresql.org/docs/current/pgstatstatements.html
- pgstattuple：https://www.postgresql.org/docs/current/pgstattuple.html
- amcheck：https://www.postgresql.org/docs/current/amcheck.html
- Logical Replication Monitoring：https://www.postgresql.org/docs/current/logical-replication-monitoring.html
- Logical Replication Conflicts：https://www.postgresql.org/docs/current/logical-replication-conflicts.html
- PostgreSQL 17 Release Notes：https://www.postgresql.org/docs/17/release-17.html
- PostgreSQL 18 Release Notes：https://www.postgresql.org/docs/18/release-18.html

### 产品资料

- Percona PMM：https://docs.percona.com/percona-monitoring-and-management/3/discover-pmm/features.html
- Prometheus postgres_exporter：https://github.com/prometheus-community/postgres_exporter
- pganalyze：https://pganalyze.com/docs
- EDB PEM：https://www.enterprisedb.com/docs/pem/latest/
- Datadog Database Monitoring：https://docs.datadoghq.com/database_monitoring/
- SolarWinds DPA：https://documentation.solarwinds.com/en/success_center/dpa/content/dpa-introduction.htm
