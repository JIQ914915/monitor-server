# SQL Server 监控能力优先级需求文档

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档状态 | 待评审 |
| 编写日期 | 2026-07-16 |
| 适用范围 | `monitor-server`、`monitor-web` 的 SQL Server 监控、预警、诊断和报告能力 |
| 正式支持版本 | SQL Server 2017、2019、2022、2025，Windows 与 Linux 上的数据库引擎 |
| 条件兼容版本 | SQL Server 2016 SP3/ESU，仅保留基础接入评估，不承诺完整新能力 |
| 暂不包含 | Azure SQL Database、Azure SQL Managed Instance、Analysis Services、Integration Services、Reporting Services |
| 支持版本（Edition） | Enterprise、Standard、Web、Express；按版本、Edition 和已启用组件探测能力，不把缺失能力误报为异常 |
| 目标用户 | 运维人员、DBA、管理人员，兼顾数据库经验较少的用户 |
| 规模基线 | 常规不超过 100 实例，重点客户约 600 实例，极限按 1000 实例验收 |

## 2. 背景与当前结论

当前仓库只有 SQL Server 数据库类型、JDBC URL 和部分通用模型预留，尚无 SQL Server 采集模块、版本适配器和完整业务页面。因此，本需求按从零建设收敛范围，不能把 MySQL 或 PostgreSQL 指标直接改名复用。

本次梳理结论如下：

1. 首期以 SQL Server 自带 DMV、目录视图、Query Store、`msdb` 历史和默认 `system_health` Extended Events 为主要数据源。
2. 首期优先回答“是否正常、哪里有风险、影响什么、如何排查”，不建设单纯的性能计数器大盘。
3. 指标分为核心指标、诊断快照和低频巡检三类，避免把高基数会话、SQL、等待和事件无限写入通用指标表。
4. Windows/Linux 主机 CPU、内存、磁盘和网络继续复用平台已有主机采集能力；数据库直连只采 SQL Server 可可靠提供的数据，不强制安装 Windows Exporter。
5. SQL Server 2017、2019、2022、2025 纳入正式范围。SQL Server 2016 的扩展支持已于 2026-07-14 结束，仅在客户具备 ESU 且有明确需求时评估兼容。
6. SQL Server 2022 及以上 DMV 权限与旧版本不同，必须按版本输出最小权限清单和能力探测结果，禁止要求统一授予 `sysadmin`。
7. Query Store 是 SQL 性能历史和计划回退的首选数据源；未启用、只读或数据不足时必须降级到 DMV 实时/累计数据并明确提示，不自动修改数据库配置。

## 3. 产品目标与非目标

### 3.1 产品目标

- 运维人员一屏判断实例和数据库是否正常、主要风险、影响范围和下一步动作。
- DBA 能从告警下钻到等待、阻塞链、死锁图、SQL、执行计划、文件 I/O、事务日志、`tempdb`、高可用和主机证据。
- 管理人员通过巡检、事件和专项报告查看健康状态、容量趋势、备份覆盖、可用性风险和待办清单。
- 数据缺失时区分真实为零、版本不支持、Edition 不支持、组件未安装、功能未启用、权限不足、采集失败和历史不足。
- 新能力复用平台现有实例、字典、告警、事件、报告、权限和保留策略，不建设平行平台。

### 3.2 明确不做

- 不自动扩容、收缩文件、杀会话、改参数、建删索引、强制执行计划、切换可用组或恢复数据库。
- 不自动启用 Query Store、blocked process report、SQL Server Audit 或新的 Extended Events 会话。
- 不默认执行 `DBCC CHECKDB`、`RESTORE VERIFYONLY` 或任何可能产生明显资源消耗的验证命令。
- 不采集密码、密码哈希、连接字符串密码、Token、Cookie、私钥或云密钥。
- 不读取备份文件内容，不替代备份软件、WSFC、Pacemaker、作业编排或变更平台。
- 不把 Azure SQL Database/Managed Instance 与自建 SQL Server 用同一套采集 SQL 隐式兼容。
- 不为 1000 实例以上的低概率规模提前引入复杂分布式架构。

## 4. 优先级与采集等级

### 4.1 需求优先级

| 优先级 | 含义 | 排期原则 |
| --- | --- | --- |
| P0 当前必须 | 决定产品能否正确接入、发现核心故障并形成基础报告 | 作为 SQL Server 首期交付 |
| P1 近期闭环 | 显著提升性能、HA、备份和故障诊断深度 | P0 稳定后实施 |
| P2 条件增强 | 依赖额外权限、组件、客户场景或专项压测 | 满足启动条件后单独立项 |
| P3 储备 | 外部系统、复杂治理或高风险自动化 | 不进入当前排期 |

优先级冲突时按以下顺序决策：可用性与数据正确性 > 用户可理解性 > 告警和报告闭环 > 诊断深度 > 指标完整度。

### 4.2 采集等级

| 等级 | 默认频率 | 数据形态 | 说明 |
| --- | --- | --- | --- |
| L1 核心时序 | 1 分钟 | 聚合指标 | 可用性、负载、连接、等待、I/O、容量、日志、`tempdb`、HA 核心状态 |
| L2 诊断快照 | 告警触发及 1～5 分钟 | 专用快照 | 活跃请求、阻塞链、长事务、Top SQL、内存授权、AG 队列 |
| L3 低频巡检 | 1 小时或 1 天 | 快照/结论 | 配置、备份、作业、版本补丁、文件增长、VLF、安全和容量趋势 |
| L4 事件数据 | 按增量游标 | 专用事件 | 死锁图、严重错误、作业失败、角色变化等，必须去重和限量 |

所有频率为默认值，实际实现必须支持按采集项配置超时、频率、Top N、最大行数、开关和保留期。

## 5. 全局强制实现规则

### 5.1 接口规则

- 所有前后端业务数据交互使用 `POST` 和 JSON 请求体。
- 查询、分页、筛选、导出、详情、探测和操作不得新增 `GET` 业务接口。
- 接口使用项目统一响应结构并执行实例数据范围和按钮权限校验。

### 5.2 状态与字典规则

- 实例状态、数据库状态、风险等级、能力状态、采集状态、HA 状态、同步状态、备份状态、作业状态、Query Store 状态、处置状态必须由字典统一维护。
- 前端从字典解析名称、颜色、排序和可选操作，不硬编码中文状态、颜色或状态枚举。
- 建议首期复用或补充以下字典：`monitor_capability_status`、`monitor_risk_level`、`sqlserver_database_state`、`sqlserver_ag_sync_state`、`sqlserver_backup_status`、`sqlserver_job_status`、`sqlserver_query_store_state`。

### 5.3 前端组件规则

- 列表统一使用 `@/components/ProTable`、`TableColumn[]` 和具名插槽。
- 新增、编辑、查看表单统一使用 `@/components/ProTable/CrudDrawer.vue`。
- 嵌入实例详情卡片的列表使用 `inner-table` 平铺样式。
- 首期复用实例概览、性能、会话、SQL、存储、高可用、告警和报告页面，原则上不新增一级菜单。
- 空数据、权限不足、版本/Edition 不支持、功能未启用和采集失败分别展示友好说明。

### 5.4 小白用户表达

- 首屏默认展示“正常、关注、告警、数据不足、暂不可用”等结论。
- 指标必须配套通俗解释，例如“数据库可连接，但事务日志空间正在快速消耗”“副本已连接，但重做速度跟不上日志产生速度”。
- 风险项至少包含结论、证据、影响对象、可能原因、排查路径和建议动作。
- 原始 DMV 字段、等待类型、SQL 文本和 XML 执行计划渐进展开，不作为首屏结论。

### 5.5 报告和智能辅助

- 每项 P0/P1 能力至少进入事件处理报告、日常巡检报告或专项分析报告中的一种。
- 报告复用现有 Word 导出和邮件推送链路。
- LLM 只能基于已采集证据生成总结、可能原因和建议，不替代规则判断，不执行高风险动作。

### 5.6 性能、安全与数据

- 默认使用最小权限只读账号，按“基础、性能诊断、增强巡检”提供分级授权模板，不要求 `sysadmin`。
- SQL Server 2019 及以下主要识别 `VIEW SERVER STATE`/`VIEW DATABASE STATE`；SQL Server 2022 及以上识别 `VIEW SERVER PERFORMANCE STATE`/`VIEW DATABASE PERFORMANCE STATE`，安全类能力单独探测。
- 重查询、跨数据库查询和 XML 解析走低频或告警触发链路，不阻塞分钟级核心采集。
- SQL 文本、登录名、客户端地址、数据库名按数据范围、权限和保留策略控制；SQL 常量默认脱敏。
- 高基数会话、SQL、等待任务、死锁和计划使用专用模型、Top N、聚合、去重与短期保留，禁止无限写入通用指标表。
- 在 100、600、1000 实例规模下验证调度完成时间、采集连接数、监控库增长和页面查询响应。

## 6. P0 当前必须监控指标

### SQLS-P0-01 版本、Edition 与能力探测

| 监控内容 | 关键字段/指标 | 主要数据源 | 频率 |
| --- | --- | --- | --- |
| 版本身份 | 产品版本、主版本、Build、Edition、EngineEdition、补丁级别、OS 平台 | `SERVERPROPERTY`、`@@VERSION`、`sys.dm_os_host_info` | 日 |
| 实例身份 | 实例名、是否集群、启动时间、排序规则、默认数据/日志路径 | `SERVERPROPERTY`、`sys.dm_os_sys_info`、目录视图 | 日 |
| 数据库发现 | 数据库 ID、名称、状态、恢复模式、兼容级别、只读、用户访问模式 | `sys.databases` | 5 分钟 |
| 能力矩阵 | Query Store、SQL Server Agent、Always On、日志传送、复制、CDC、TDE、XEvent、主机关联 | 版本、Edition、组件和权限探测 | 日及配置变更后 |
| 权限完整性 | 基础连接、服务器/数据库性能状态、`msdb` 历史、XEvent、HA DMV 的可用性 | `HAS_PERMS_BY_NAME` 与受控探测 | 日及失败后 |

验收标准：

- 正式支持版本各建立独立版本契约测试，不使用“高版本一律按旧版本回退”宣称支持。
- Express 没有 SQL Server Agent 时显示“不支持”，不产生 Agent 离线告警。
- 权限不足只限制对应采集项，不导致整个实例不可监控，也不把缺失值写成 `0`。

### SQLS-P0-02 可用性与数据库健康

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 连接可用性 | 连接成功率、连接耗时、连续失败次数、最近成功时间 | 无法连接、连接变慢、恢复 | JDBC 探测 |
| 实例运行 | 实例启动时间、运行时长、启动时间变化 | 非计划重启、频繁重启 | `sys.dm_os_sys_info` |
| 数据库状态 | `state_desc`、用户访问、只读、恢复状态 | OFFLINE、SUSPECT、RECOVERY_PENDING、EMERGENCY 等 | `sys.databases` |
| 页面损坏线索 | suspect page 新增数、最近错误、受影响数据库/页 | 新增疑似损坏页，需人工核查 | `msdb.dbo.suspect_pages` |
| 严重错误 | 20～25 级错误、I/O/一致性相关错误增量 | 实例或数据库高风险 | 默认 `system_health` 与受控错误源 |

要求：严重错误读取必须使用增量游标、去重和上限；不能把历史累计事件每次重复告警。

### SQLS-P0-03 负载、CPU 与调度器

| 指标组 | 核心指标 | 计算/解释 | 主要数据源 |
| --- | --- | --- | --- |
| 吞吐 | Batch Requests/sec、Transactions/sec、SQL Compilations/sec、Re-Compilations/sec | 统一按相邻采样差值换算速率 | `sys.dm_os_performance_counters` |
| SQL CPU | SQL Server 进程 CPU、CPU 压力趋势 | 与主机 CPU 关联，避免只凭单点阈值 | SQL Server/主机采集 |
| 调度器 | runnable tasks、current tasks、active workers、load factor | 持续 runnable queue 表示 CPU/调度压力 | `sys.dm_os_schedulers` |
| 工作线程 | worker 使用量、最大线程、pending tasks | 识别线程耗尽和连接堆积 | `sys.dm_os_sys_info`、调度器 DMV |
| 并行度证据 | CXPACKET/CXCONSUMER 等等待增量、并行查询证据 | 只做诊断证据，不以单一等待直接判错 | 等待 DMV、请求 DMV |

阈值策略：首期提供保守模板和持续时长，允许按实例覆盖；不能把某个通用行业数值硬编码为所有实例的固定告警线。

### SQLS-P0-04 内存与缓存

| 指标组 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 服务器内存 | Total Server Memory、Target Server Memory、max server memory | 目标差距持续扩大、配置与主机不匹配 | 性能计数器、配置 |
| 进程/OS 内存 | process physical/virtual memory、available physical memory、memory state | 主机或进程内存压力 | `sys.dm_os_process_memory`、`sys.dm_os_sys_memory` |
| Buffer Pool | Page Life Expectancy、Buffer Cache Hit Ratio、Page reads/writes/sec、Lazy Writes/sec | 缓存压力趋势，按 NUMA/实例规模解释 | 性能计数器 |
| 查询内存授权 | Memory Grants Pending/Outstanding、等待时长、请求量 | 查询因内存授权排队 | 性能计数器、`sys.dm_exec_query_memory_grants` |
| 内存去向 | Top memory clerks、plan cache 使用 | 告警时提供主要内存消费者 | `sys.dm_os_memory_clerks`、缓存 DMV |

要求：Buffer Cache Hit Ratio、PLE 不采用跨实例固定单阈值；默认以基线偏离、持续时间和内存授权等待共同判断。

### SQLS-P0-05 连接、会话、请求与事务

| 监控内容 | 核心指标/字段 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 连接 | 当前连接、用户连接、登录速率、登录失败增量 | 连接逼近上限、登录异常增加 | 性能计数器、会话 DMV、错误事件 |
| 活跃请求 | running/runnable/suspended 数量、耗时、CPU、读写、等待 | 长时间运行、资源消耗异常 | `sys.dm_exec_requests` |
| 会话分布 | 按数据库、登录、主机、程序的活跃/休眠分布 | 识别连接池或来源异常 | `sys.dm_exec_sessions`、`sys.dm_exec_connections` |
| 长事务 | 最老事务时长、会话、数据库、日志占用线索 | 阻止日志截断、阻塞或版本存储增长 | `sys.dm_tran_*`、请求/会话 DMV |
| 异常睡眠事务 | sleeping 且存在打开事务的会话 | 客户端未提交/回滚 | 会话与事务 DMV |

SQL 文本、登录名和客户端信息只对有权限用户展示；列表使用 Top N 和分页，不采集全量连接历史。

### SQLS-P0-06 等待统计

| 监控层级 | 核心指标 | 处理要求 | 主要数据源 |
| --- | --- | --- | --- |
| 实例累计等待 | wait time 增量、signal wait 增量、task count 增量 | 保存相邻采样差值，识别实例重启/计数器重置 | `sys.dm_os_wait_stats` |
| 等待分类 | CPU、I/O、锁、日志、内存、网络、并行、HA、其他 | 分类映射版本化维护，不散落在页面代码 | 平台字典/规则 |
| 当前等待 | 当前等待会话、资源、阻塞者、持续时间 | 仅保留 Top N 诊断快照 | `sys.dm_os_waiting_tasks` |
| SQL 等待 | Query Store wait category、查询、计划、时间窗口 | Query Store 可用时关联到 SQL | Query Store 目录视图 |

要求：过滤官方/社区公认的空闲与后台等待，但保留原始值供 DBA 展开；等待排名必须基于时间窗口增量，不能直接用自启动以来累计值下结论。

### SQLS-P0-07 文件、磁盘 I/O 与容量

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 文件容量 | data/log 文件大小、已用、可用、增长方式、最大值、文件组 | 空间不足、百分比增长、上限风险 | `sys.master_files`、文件空间 DMV |
| 文件 I/O | reads/writes、bytes、stall、平均读/写延迟 | 定位数据库和文件级慢 I/O | `sys.dm_io_virtual_file_stats` |
| 增长事件 | 文件自动增长次数、耗时、文件、增长量 | 频繁增长、增长阻塞风险 | 默认 XEvent/历史快照差异 |
| 宿主磁盘 | 挂载点总量、可用量、预计耗尽时间 | 文件可增长但磁盘不足 | `sys.dm_os_volume_stats`，并优先关联主机采集 |
| 容量趋势 | 7/30 日增长、预计耗尽日期、增长最快数据库/文件 | 历史不足时不预测 | 平台历史计算 |

要求：平均延迟由采样区间内 `io_stall` 差值除以 I/O 次数差值计算；处理计数器重置和零分母。容量预测首期使用可解释线性趋势，不引入机器学习。

### SQLS-P0-08 事务日志与 VLF

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 日志空间 | 总量、已用量、使用率、增长速度 | 日志接近满、快速增长 | `sys.dm_db_log_space_usage` |
| 截断阻塞 | `log_reuse_wait_desc`、持续时间 | 备份、长事务、AG/复制等阻止复用 | `sys.databases` |
| 日志吞吐 | Log Bytes Flushed/sec、Log Flushes/sec、平均 flush wait | 日志盘或事务提交瓶颈 | 性能计数器、文件 I/O |
| VLF | VLF 总数、活动 VLF、异常增长趋势 | VLF 过多影响恢复/复制 | `sys.dm_db_log_info` 等版本适配视图 |
| 恢复模式匹配 | 恢复模式、最后日志备份时间 | FULL/BULK_LOGGED 长期无日志备份 | `sys.databases`、`msdb` 备份历史 |

要求：日志使用率告警必须同时展示 `log_reuse_wait_desc`、最近日志备份和最老事务线索，不能只提示“扩容”。

### SQLS-P0-09 tempdb

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 空间构成 | user objects、internal objects、version store、unallocated | 判断主要空间消费者 | `sys.dm_db_file_space_usage` |
| 文件布局 | 文件数、大小差异、增长配置、所在卷 | 文件不均衡、增长配置风险 | `tempdb.sys.database_files`、卷信息 |
| 分配争用 | PAGELATCH 类等待增量、热点文件 | 分配争用风险 | 等待与当前任务 DMV |
| 版本存储 | version store 大小、增长速度、最老相关事务 | 长事务导致版本存储增长 | `tempdb` 空间与事务 DMV |
| 溢写证据 | sort/hash spill 事件或计数、相关 SQL | 内存授权不足或估算偏差 | Query Store/XEvent（能力可用时） |

要求：`tempdb` 风险必须区分容量、文件布局、分配争用和版本存储四类原因，不能统一显示为“tempdb 异常”。

### SQLS-P0-10 阻塞、锁与死锁

| 监控内容 | 核心指标/快照 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 阻塞概况 | 被阻塞会话数、最长阻塞时长、链深度、影响数据库 | 持续阻塞和影响面 | 请求/等待 DMV |
| 根阻塞者 | head blocker、SQL、事务开始时间、登录/程序、持有资源 | 找到根会话和未提交事务 | 请求、会话、事务、锁 DMV |
| 锁压力 | lock waits、timeouts、escalations/sec、deadlocks/sec | 锁争用趋势 | 性能计数器、等待 DMV |
| 死锁事件 | 时间、数据库、victim、process/resource list、XML graph | 可视化死锁关系和受影响 SQL | `system_health` 的 `xml_deadlock_report` |

要求：死锁读取复用默认 `system_health`，首期不自动创建 XEvent 会话；死锁 XML 按指纹去重并设置大小与保留上限。产品只给出处置建议，不提供默认“杀会话”动作。

### SQLS-P0-11 Top SQL 与 Query Store 健康

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| Top SQL | CPU、elapsed、logical/physical reads、writes、executions、rows、平均值 | 找到主要资源消费者 | Query Store；降级为 `sys.dm_exec_query_stats` |
| 长查询 | 当前耗时、进度、等待、阻塞、读写、SQL/计划句柄 | 运行中慢 SQL | `sys.dm_exec_requests` |
| 计划历史 | query/plan id、计划数、时间窗、运行统计变化 | 计划变化与性能回退 | Query Store 目录视图 |
| Query Store 状态 | actual state、desired state、只读原因、当前/上限容量、capture mode、数据刷新 | Query Store 不可用或容量风险 | `sys.database_query_store_options` |
| 编译与缓存 | compilations、recompilations、plan cache 命中/膨胀线索 | 编译压力或 ad hoc 计划过多 | 性能计数器、缓存 DMV |

要求：

- Query Store 未启用时显示“未启用，无法提供历史计划和 SQL 等待”，不自动开启。
- SQL Server 2017 及以上可用时采集 Query Store 等待维度；不同版本能力由适配器声明。
- SQL Server 2022 新建数据库默认开启 Query Store，但升级/还原数据库可能保留原设置，必须逐库探测。
- 执行计划只解析展示，不执行 SQL，不自动强制计划或应用 Query Store Hint。

### SQLS-P0-12 备份覆盖与恢复准备度

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 备份新鲜度 | 最近 full/diff/log 备份时间、距今天数/分钟 | 超过策略窗口未备份 | `msdb.dbo.backupset` |
| 备份结果 | 成功/失败、耗时、大小、压缩比、吞吐、checksum 标记 | 失败、变慢、异常变小 | `msdb` 备份历史 |
| 覆盖关系 | 数据库、恢复模式、备份链概况 | FULL 模式缺少日志备份、数据库未覆盖 | `sys.databases`、`msdb` |
| 恢复证据 | 最近人工恢复演练时间、RTO、验证结果 | 长期未演练、验证失败 | 平台登记记录 |

要求：

- 明确提示“存在备份记录不等于备份可恢复”。
- 首期只读取历史并支持登记外部人工恢复演练结果，不访问备份介质，不自动执行验证或恢复。
- 系统数据库与用户数据库使用不同默认策略模板，模板可由用户调整。

### SQLS-P0-13 Always On 可用组核心健康

仅对能力探测确认已启用 Always On 的实例采集。

| 监控内容 | 核心指标 | 结论/告警方向 | 主要数据源 |
| --- | --- | --- | --- |
| 副本角色/连接 | primary/secondary、connected state、operational state | 副本断连、角色变化 | `sys.dm_hadr_availability_replica_states` |
| 同步健康 | synchronization state/health、is_suspended、suspend reason | 未同步、数据移动暂停 | `sys.dm_hadr_database_replica_states` |
| RPO 风险 | log send queue KB、log send rate、估算发送秒数、last commit lag | 发送积压和潜在数据丢失窗口 | AG DMV、性能计数器 |
| RTO 风险 | redo queue KB、redo rate、估算重做秒数 | 故障切换后恢复时间风险 | AG DMV、性能计数器 |
| 可用组状态 | primary replica、listener、数据库参与完整性 | 数据库缺席、可用组不完整 | AG 目录视图/DMV |

要求：队列大小必须结合发送/重做速率和持续时间解释；同步提交与异步提交使用不同告警模板。只提供人工排查和切换建议，不执行故障转移。

### SQLS-P0-14 健康结论、告警与报告

- 将可用性、数据库状态、CPU/调度、内存、连接、等待、I/O、容量、日志、`tempdb`、阻塞、SQL、备份和 HA 汇总为实例级状态。
- 同一根因的相关指标合并为一个事件。例如磁盘空间不足、文件自动增长和日志接近满应关联展示，避免告警风暴。
- 告警详情保留触发前后核心指标和诊断快照，形成证据、影响、原因、路径和建议闭环。
- 日常巡检报告至少包含：版本与能力、实例/数据库健康、资源压力、Top 等待、Top SQL、容量与日志、`tempdb`、阻塞/死锁、备份、HA 和风险清单。
- 事件处理报告至少包含：时间线、影响范围、指标证据、阻塞/死锁/SQL/HA 快照、人工处置记录和恢复验证。

验收标准：实例概览一屏回答“是否正常、主要问题、影响什么、下一步做什么”；从核心告警进入后不超过三次操作可看到主要诊断证据。

## 7. P1 近期闭环指标

### SQLS-P1-01 SQL 性能回退与计划对比

- 建立 Query Store 分时段基线，识别新 SQL、突增 SQL、变慢 SQL、计划变化和等待类型变化。
- 对比计划节点、访问方式、并行度、估算行数、内存授权和 spill 证据。
- 输出候选优化方向和验证方式，不自动创建索引、不强制计划、不应用 Hint。
- Query Store 数据不足、被清理或只读时降低结论置信度。

### SQLS-P1-02 SQL Server Agent 作业

- 采集 Agent 服务可用性、作业启停、最近运行状态、持续时间、失败步骤、下次运行时间和连续失败次数。
- 基于同一作业历史识别“运行超时”和“未按计划运行”，阈值使用历史基线或用户策略。
- Express 或未安装 Agent 时显示“不支持”；不自动重跑、停止或修改作业。
- 作业失败进入巡检和事件报告，敏感命令文本默认不采集。

### SQLS-P1-03 日志传送

- 采集 primary backup、copy、secondary restore 的最近时间、阈值、延迟、状态和作业结果。
- 区分“未产生新日志”“备份失败”“复制失败”“还原失败”“监控服务器数据过期”。
- 仅在探测到日志传送配置后展示，不与 Always On 状态混用。

### SQLS-P1-04 复制与 CDC

- 按能力探测采集发布/分发/订阅状态、延迟、失败代理、积压和最后同步时间。
- CDC 采集启用状态、capture/cleanup job 状态、日志扫描延迟和保留风险。
- Replication、CDC 和 Agent 的依赖关系在告警中明确展示；Express subscriber 等 Edition 差异不误报。

### SQLS-P1-05 配置漂移与版本风险

- 保存关键 `sp_configure`、数据库配置、兼容级别、文件增长、恢复模式、Query Store 配置的变更快照，相同快照不重复存储。
- 识别 max server memory、MAXDOP、cost threshold、`tempdb`、auto shrink、自动增长和备份压缩等常见风险。
- 对比实例、AG 成员和预设模板，输出差异、发现时间、影响和人工变更建议。
- 采集 Build 后对照平台维护的离线版本元数据标识“版本较旧”；不在采集任务中访问外网。

### SQLS-P1-06 索引与统计信息候选建议

- 采集高成本未使用/低使用索引、重复/重叠索引、missing index 线索、统计信息更新时间和修改量。
- 建议必须关联 SQL、读写成本、观察窗口、磁盘占用和维护风险。
- DMV 自重启清零时明确标识观察窗口，不把短期未使用直接判定为可删除。
- 只输出候选建议，不自动创建、删除、重建索引或更新统计信息。

### SQLS-P1-07 主机与数据库关联诊断

- 已关联主机采集时，同屏关联 Windows/Linux CPU、内存、磁盘空间、磁盘延迟、网络和 SQL Server 进程状态。
- 告警时间线关联主机资源、数据库等待、Top SQL、阻塞和配置变更。
- 未关联主机时继续提供数据库侧结论，并明确“缺少主机数据，无法确认宿主资源压力”。

### SQLS-P1-08 专项报告

- SQL 性能专项：Top SQL、等待、计划变化、阻塞/死锁和候选建议。
- 容量专项：数据库/文件/日志/`tempdb`/磁盘趋势和预计耗尽日期。
- 高可用专项：AG、日志传送、复制健康、RPO/RTO 风险和故障时间线。
- 备份恢复专项：备份覆盖、失败、耗时趋势、恢复演练记录和逾期风险。

## 8. P2 条件增强

| 能力 | 启动条件 | 范围 |
| --- | --- | --- |
| SQL Server 2016 兼容 | 客户具备 ESU、明确存量需求和专项测试环境 | 只承诺经验证的基础指标，页面持续提示版本生命周期风险 |
| Azure SQL | 明确客户需求并完成产品边界评审 | Azure SQL Database 与 Managed Instance 分别建立能力矩阵和采集适配，不能沿用本地实例假设 |
| 自定义 XEvent 会话 | 默认 `system_health` 证据不足，客户允许配置且开销压测通过 | 提供脚本和人工确认流程，平台监控会话自身丢事件、文件大小和状态 |
| SQL Server Audit/安全基线 | 客户有合规需求并提供独立最小权限 | 登录失败、角色/授权变化、TLS/TDE、审计状态；不保存密码哈希 |
| Resource Governor | Enterprise/相应 Edition 客户明确使用 | 资源池/工作负载组 CPU、内存、请求和限流状态 |
| In-Memory/Columnstore | 客户实际使用且采集开销验证通过 | 内存优化表、checkpoint 文件、columnstore rowgroup 健康和压缩效率 |
| Service Broker | 客户实际使用 | 队列积压、禁用队列、传输错误和处理延迟 |
| DBCC/备份验证结果接入 | 客户已有外部执行流程 | 只登记或读取结果，平台不默认执行重任务 |

## 9. P3 储备

- 自动扩容、自动收缩、自动杀会话、自动改配置、自动索引处置、自动强制计划和自动 HA 切换。
- 直接控制 WSFC/Pacemaker、备份软件、存储阵列或云控制面。
- 通用 T-SQL 自定义探针市场和可执行脚本中心。
- 基于复杂机器学习的异常检测、容量预测和自动根因图谱。
- 跨 APM、发布、CMDB、工单和变更平台的通用关联中心。

P3 能力启动前必须重新评审客户数量、使用频率、误报率、权限风险、实施成本和对报告闭环的实际增益。

## 10. 指标落库与采集设计约束

| 数据类型 | 落库建议 | 默认保留策略 |
| --- | --- | --- |
| L1 核心聚合指标 | 复用通用指标模型，标签限制为实例/数据库/有限维度 | 原始分钟级短期，按现有小时/日聚合策略长期保留 |
| 文件/数据库/AG 对象指标 | 使用容量/对象专用模型 | 分钟或 5 分钟快照，按对象聚合 |
| 活跃请求/阻塞链 | 专用诊断快照，关联事件 ID | 正常短保留，关联事件按事件策略保留 |
| Top SQL/计划 | SQL 指纹、计划指纹、时间窗口聚合，SQL 文本与统计分离 | Top N 和分层保留 |
| 死锁/XEvent | 事件专表，XML 压缩、限长、指纹去重 | 按事件保留策略清理 |
| 配置/能力 | 仅变化时保存快照 | 保留变更历史 |
| 备份/作业 | 保存摘要和状态，不复制完整 `msdb` | 日常摘要长期、明细按策略清理 |

指标命名必须表达单位和语义；累计计数器保存原值和采样时间，由采集层识别重启/重置并计算差值。禁止将 `NULL`、无权限或不支持统一转换为零。

## 11. 页面信息架构

| 页面/区域 | 默认内容 | 高级下钻 |
| --- | --- | --- |
| 实例概览 | 健康状态、主要风险、连接、负载、资源、容量、备份、HA 摘要 | 能力完整性、版本、主机关联、原始证据 |
| 性能 | CPU/调度、内存、吞吐、等待、I/O 趋势 | memory clerks、调度器、文件延迟、等待明细 |
| 会话与锁 | 活跃请求、长事务、阻塞链、死锁列表 | SQL、事务、锁资源、死锁图 |
| SQL 分析 | Top SQL、长查询、性能回退、计划变化 | Query Store 时间窗、计划对比、等待和候选建议 |
| 存储 | 数据/日志/`tempdb`、磁盘、增长和预测 | 文件组、文件 I/O、VLF、增长事件 |
| 高可用 | AG/日志传送/复制状态与风险 | 副本队列、速率、RPO/RTO 证据和时间线 |
| 运维保障 | 备份、Agent 作业、恢复演练、配置漂移 | 明细、失败步骤、策略和历史对比 |

所有列表使用 ProTable；查看和配置使用 CrudDrawer；状态使用字典组件。首屏不展示大量原始性能计数器。

## 12. 实施顺序

| 批次 | 范围 | 完成标志 |
| --- | --- | --- |
| A 基础接入 | SQL Server JDBC 驱动、采集模块、2017/2019/2022/2025 适配器、实例表单、能力探测 | 四版本连接、版本契约、权限降级和采集隔离验证通过 |
| B 核心健康 | P0-02～P0-09：可用性、负载、内存、连接、等待、I/O、容量、日志、`tempdb` | 分钟级采集、字典状态、告警模板和 100/600/1000 实例压测通过 |
| C 故障诊断 | P0-10～P0-11：阻塞、死锁、Top SQL、Query Store | 告警快照、SQL 脱敏、死锁去重和计划展示通过 |
| D 运维保障 | P0-12～P0-14：备份、AG、健康汇总和报告 | 巡检/事件报告、HA 降级和小白表达验收通过 |
| E 近期闭环 | P1 作业、日志传送、复制、配置漂移、索引候选、主机关联、专项报告 | 每项形成状态、证据、建议、告警和报告闭环 |
| 条件批次 | P2 | 满足启动条件后分别立项和验收 |

每批次独立提交、独立验证、可独立回滚，不将全部能力合并为一个大提交。

## 13. 统一验收清单

### 13.1 功能与兼容

- SQL Server 2017、2019、2022、2025 在 Windows/Linux 适用范围内通过核心采集契约测试。
- Enterprise、Standard、Express 的能力差异正确；不支持能力不产生错误告警。
- 数据库重启、DMV 计数器重置、Query Store 关闭/只读、权限不足均有正确处理。
- 连接失败、日志接近满、`tempdb` 增长、持续阻塞、死锁、备份逾期和 AG 积压能形成可理解事件。

### 13.2 项目规则

- 新增业务接口全部使用 POST 和 JSON body。
- 所有业务状态由字典维护；页面无新增硬编码状态文案、颜色和状态枚举。
- 列表使用 ProTable，查看/配置表单使用 CrudDrawer。
- P0/P1 能力至少进入一种报告，支持 Word 和邮件推送。
- 不存在自动执行高风险数据库动作的入口。

### 13.3 性能与数据

- 100、600、1000 实例规模下，分钟级任务在调度窗口内完成，失败实例不阻塞其他实例。
- 每个采集项具备超时、行数上限、频率、开关和失败隔离。
- 高基数数据有 Top N、专用模型、去重和保留清理策略。
- 累计计数器差值、平均 I/O 延迟和 AG 预计队列时间的计算通过单元测试。

### 13.4 安全

- 最小权限账号可完成基础监控；增强能力缺权时只显示对应能力受限。
- 不要求 `sysadmin`，不输出数据库异常堆栈和授权 SQL 中的真实账号密码。
- SQL 常量默认脱敏，SQL 文本、登录名、客户端地址受数据范围和权限控制。
- 采集日志不打印 JDBC 密码、连接字符串密码、Token 或其他敏感信息。

## 14. 官方依据与产品对标

### 14.1 Microsoft 官方依据

- [Monitor and Tune for Performance](https://learn.microsoft.com/en-us/sql/relational-databases/performance/monitor-and-tune-for-performance?view=sql-server-ver17)：确认 DMV、性能计数器、Query Store 和 Extended Events 是官方主要监控手段。
- [Monitor performance by using the Query Store](https://learn.microsoft.com/en-us/sql/relational-databases/performance/monitoring-performance-by-using-the-query-store?view=sql-server-ver17)：Query Store 提供查询、计划、运行统计和 SQL Server 2017 及以上的等待统计。
- [System Dynamic Management Views](https://learn.microsoft.com/en-us/sql/relational-databases/system-dynamic-management-views/system-dynamic-management-views?view=sql-server-ver17)：确认 SQL Server 2022 及以上性能状态权限变化。
- [Deadlocks Guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-deadlocks-guide?view=sql-server-ver17)：推荐通过 `xml_deadlock_report`，默认 `system_health` 已采集死锁图。
- [Monitor Performance for Availability Groups](https://learn.microsoft.com/en-us/sql/database-engine/availability-groups/windows/monitor-performance-for-always-on-availability-groups?view=sql-server-ver17)：确认 log send/redo queue、速率与 RPO/RTO 诊断关系。
- [Backup history and header information](https://learn.microsoft.com/en-us/sql/relational-databases/backup-restore/backup-history-and-header-information-sql-server?view=sql-server-ver17)：确认 `msdb` 保存备份与恢复历史，但备份记录不能替代恢复验证。
- [SQL Server transaction log architecture and management guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-log-architecture-and-management-guide?view=sql-server-ver17)：确认日志空间、自动增长与 VLF 风险。
- [SQL Server 2025 lifecycle](https://learn.microsoft.com/en-us/lifecycle/products/sql-server-2025)、[SQL Server 2022 lifecycle](https://learn.microsoft.com/en-us/lifecycle/products/sql-server-2022)、[SQL Server 2019 lifecycle](https://learn.microsoft.com/en-us/lifecycle/products/sql-server-2019)、[SQL Server 2017 lifecycle](https://learn.microsoft.com/en-us/lifecycle/products/sql-server-2017)、[SQL Server 2016 lifecycle](https://learn.microsoft.com/en-us/lifecycle/products/sql-server-2016)：用于确定正式支持与条件兼容范围。

### 14.2 成熟产品对标结论

- Datadog Database Monitoring 已覆盖查询级指标、实时/历史查询快照、等待分析、数据库负载、执行计划和阻塞洞察，说明 SQL Server 产品不能只停留在主机与性能计数器。
- Redgate Monitor 强调机器、实例、数据库分层采集，以及从告警提供足够证据帮助非资深用户判断原因和处理方式，与本项目“小白友好、结论优先”一致。
- SolarWinds DPA 强调以等待时间和 SQL 为中心的性能归因，验证了“等待—SQL—资源”下钻链路的必要性。
- Prometheus `windows_exporter` 的 MSSQL collector 适合暴露 SQL Server Performance Objects，但不能替代 Query Store、阻塞链、备份、作业和 HA 等数据库语义采集，因此本项目不把它作为首期强依赖。

对标后的产品取舍是：首期必须同时具备基础时序指标、数据库语义、诊断快照和报告闭环；不追求一次性覆盖所有计数器，也不复制商业产品的高风险自动调优能力。
