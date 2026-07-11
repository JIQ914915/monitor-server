# monitor-platform

数据库监控产品后端（Maven 多模块）

根包 `com.lzzh.monitor`，面向「多数据库类型 × 多版本」可扩展：**新增类型 = 新增模块、新增版本 = 新增适配器，不改核心**。

## 环境要求

- JDK 21（本项目 `maven.compiler.release=21`）
- Maven 3.9+
- PostgreSQL 16（+ TimescaleDB）、Redis（可选）、xxl-job-admin（调度中心，独立部署）

## 模块结构

```
monitor-platform/                父 POM（BOM/版本/插件统一）
├── monitor-common/              统一返回/异常/JWT/枚举(DbType,DbVersion...)/常量/工具/操作日志注解
├── monitor-dao/                 实体 + MyBatis-Plus Mapper + TimescaleDB 时序写入抽象
├── monitor-service/             领域服务（实例、数据库类型映射 ...）
├── monitor-admin/        ★可执行  Web API + Spring Security/JWT + Druid + Knife4j
├── monitor-collector-spi/ ★扩展核心 采集 SPI（接口/工厂/注册/注解/版本路由/模型），无具体库实现
├── monitor-collector-mysql/ ★实现  MySQL 采集（5.6/5.7/8.0 版本适配器）
└── monitor-collector/    ★可执行  xxl-job 执行器，聚合 SPI + 各库实现
```

## 启动

1. 准备 PostgreSQL（库 `monitor`）、xxl-job-admin（`:8081`）。
2. 构建：`mvn clean package -DskipTests`
3. 启动 Web API：`java -jar monitor-admin/target/monitor-admin.jar`（默认 `:8080`）
   - 首次启动 **Flyway 自动建表 + 种子数据**（`db/migration/V1、V2`）。
   - 默认管理员：**`admin` / `123456`**（BCrypt，生产首次登录请修改）。预设角色：管理员/DBA/运维/只读。
   - 前端联调：将 `monitor-web/.env.development` 的 `VITE_USE_MOCK` 改为 `false`，`VITE_API_BASE=/api`（网关前缀），各接口 url 自带版本（如 `/v1/auth/login`），Vite 将 `/api` 反代到 `:8080`。
   - 业务 API 统一前缀：`/api/v1/...`（便于日后并列 `/api/v2`）。
   - API 文档：`http://localhost:8080/doc.html`
   - Druid 监控：`http://localhost:8080/druid/`
4. 启动采集执行器：`java -jar monitor-collector/target/monitor-collector.jar`
   - 在 xxl-job-admin 新建 JobHandler 任务（完整清单见下节「xxl-job 任务映射」）。

## xxl-job 任务映射

业务定时任务统一由 xxl-job-admin 调度（进程内仅保留连接健康检查等维护性 `@Scheduled`），需在 xxl-job-admin 逐个配置以下 JobHandler（`monitor-collector` 中 `@XxlJob`）：

| JobHandler | 说明 | 建议调度 | 路由策略 |
| --- | --- | --- | --- |
| `minuteCollectJobHandler` | 数据库分钟级采集（MySQL / PG 同一入口） | `0 * * * * ?`（每分钟第 0 秒） | 分片广播 |
| `hostCollectJobHandler` | 主机指标采集（node/windows_exporter） | `10 * * * * ?`（每分钟第 10 秒，与数据库采集错峰） | 分片广播 |
| `hourlyCollectJobHandler` | 小时级采集 | `30 0 * * * ?`（整点第 30 秒） | 分片广播 |
| `dailyCollectJobHandler` | 天级采集 | `40 0 2 * * ?`（02:00:40） | 分片广播 |
| `alertEvaluateJobHandler` | 告警规则 + 场景评估 | `15 * * * * ?`（每分钟第 15 秒） | 分片广播 |
| `alertNotifyRetryJobHandler` | 告警通知失败重试 | `45 * * * * ?`（每分钟第 45 秒） | 第一个 |
| `healthCalculateJobHandler` | 健康评分（全量实例，不分片） | `20 */5 * * * ?`（每 5 分钟第 20 秒） | 第一个 |
| `baselineDetectJobHandler` | 基线学习与异常检测（全量实例，不分片） | `0 10 * * * ?`（每小时第 10 分钟，避开整点采集） | 第一个 |
| `reportGenerateJobHandler` | 定时报告扫描生成（report_schedule 到期任务） | `0 0/10 * * * ?`（每 10 分钟） | 第一个 |
| `retentionCleanupJobHandler` | 普通表保留清理（alert_event / sys_oper_log） | `0 30 3 * * ?`（每日 03:30） | 第一个 |

> 运行模式均为 BEAN，任务参数留空。采集/评估类阻塞策略选「丢弃后续调度」（下一轮自然补偿，失败重试次数建议 0）；
> 「第一个」路由的任务内部为全库级逻辑，多节点部署时避免重复执行，单节点部署不受影响。

> 建议调度按 **秒级错峰** 配置：分钟级采集固定在第 0 秒，其余任务错开到不同秒位，
> 避免分钟/小时/天级任务在整点同一秒触发造成目标库查询与监控库写入的瞬时叠加。
> 只偏移秒位、不偏移分钟位：采集时间戳的 HH:mm 不变，1m 连续聚合（`metric_data_1h_cagg`
> 按整点分桶）与小时级原始表的时间点保持一致，图表与差值计算口径不受影响。

collector 启动依赖 xxl-job-admin 可用（执行器注册地址：`xxl.job.admin-addresses`，环境变量 `XXL_JOB_ADMIN`）。

## 按实例规模部署（采集容量规划）

写入链路已内建三层削峰：xxl-job 秒级错峰（见上节）、时序写入 chunk 分批
（`collector.ts-write.*ChunkSize`，默认 100~200 行/批）、节点内固定线程池限流
（`collector.concurrency.pool-size`，默认 8，即单节点同时采集的最大实例数）。
目标库连接占用：每实例常驻 1 条只读长连接（分钟级采集与告警评估共用、实例锁互斥），
小时级/天级重查询走临时连接用完即关（期间短暂升至 2 条），不会阻塞分钟级采集。
监控库写入量本身不是瓶颈（单实例分钟级最坏约 250 行/分钟，各采集项均有行数上限），
**容量规划的核心指标是"一轮分钟级采集能否在 1 分钟内跑完"**：
一轮耗时 ≈ 单实例采集耗时（约 1~2s） × ⌈分片实例数 ÷ pool-size⌉。
超过调度周期时 `CollectRunner` 的轮锁会丢弃后续轮次并打 warn 日志（"上一轮采集尚未结束，丢弃本轮调度"），
出现该日志即需要加采集节点或调大 pool-size。

### ≤100 实例（典型场景）

- **collector**：1 个节点，默认配置即可；希望整轮更快收尾可将 `pool-size` 调至 16。
- **监控库**：PG + TimescaleDB，4C8G、SSD。分钟级稳态写入约 1~2.5 万行/分钟，负载很轻。
- **xxl-job**：按上节错峰 cron 配置，采集类任务路由策略选「分片广播」，阻塞策略选「丢弃后续调度」。

### ~1000 实例（极限场景）

- **collector**：**4 个节点分片广播**（每节点约 250 实例），`collector.concurrency.pool-size=32`。
  每节点一轮 8 个批次、约 12~16s 采完，1 分钟周期内富余；单节点故障时 xxl-job 按存活执行器自动重新分片。
- **监控库**：8C16G 起步、NVMe SSD；分钟级稳态写入约 10~25 万行/分钟（1.7k~4.2k 行/秒），
  批量 upsert 下 TimescaleDB 可承受。Druid 连接池 `maxActive` 配 64 即可覆盖各节点写入并发。
- **写入参数**：`ts-write.*ChunkSize` 保持默认；若 `slowLogMs` 慢写入告警（默认 >2s）频繁出现，
  优先排查监控库 IO，而不是调大 chunk。

### 观测手段（已内建）

- `collect_log.duration_ms`：单实例采集耗时分布，前端「采集管理」可视化查看。
- collector 日志 warn「上一轮\[频率\]采集尚未结束，丢弃本轮调度」：分片容量不足的直接信号。
- 各时序 writer 的「写入偏慢」warn（`collector.ts-write.slow-log-ms`，默认 2000ms）：监控库写入变慢的信号。

## 关键约定

- 统一返回体 `Result<T>`（`code===0` 成功），与前端 `api/request.ts` 解包一致。
- 写操作走 POST；按钮级权限通过前端 `v-permission="'menu:action'"` 指令控制按钮显隐，权限点在「菜单管理 → 按钮权限」维护，分配给角色后即时生效。
- 多角色权限并集：登录后由 `JwtAuthFilter` 加载用户角色 → `RolePermissionResolver` 计算权限并集。
- 数据保留为系统级、仅管理员可写。

## 实例数据范围校验（DataScopeService）

角色的「数据范围」（`sys_role.data_scope`：`all` 全部实例 / `self` 仅本人负责 / `custom` 指定分组，见 `data_scope_groups`）与实例自身配置的负责人 A/B、所属分组**取并集**共同生效，统一由 `monitor-service` 的 `DataScopeService` 组件解析，避免各处重复实现口径不一致的过滤逻辑：

- `CurrentUserHolder`（`ThreadLocal`）：`JwtAuthFilter` 认证通过后写入当前用户 ID + 角色码，请求结束 `finally` 清理，供 Service 层无侵入读取。
- `DataScopeService#currentScope()`：读取 `CurrentUserHolder`，按用户所有角色的 `data_scope` 取"最宽"口径（任一角色为 `all` 则整体 `all`），再与"本人负责的实例"（`owner_a_id`/`owner_b_id`）、"本人所属分组下的实例"取并集，返回 `DataScope`（`isUnrestricted()` 或具体可见 `instanceIds`）。
- 已接入：`InstanceServiceImpl`（列表/详情/编辑/删除均校验）、`AlertRuleServiceImpl`（按规则所属实例校验）、`AlertEventServiceImpl`（按事件所属实例校验，含通知记录/操作流水查询）。
- 新增需要数据范围过滤的模块时，注入 `DataScopeService`，在 `buildWrapper`/`getById` 等处调用 `currentScope()` 并对 `instanceId` 做 `IN` 过滤或 `allows(id)` 校验即可，无需重复实现取并集逻辑。

## 告警评估并发安全

xxl-job 分片广播保证同一实例的规则评估固定落在一个节点上；
极端时序下的并发建单由 `alert_event` 的 `dedup_key` 部分唯一索引（活跃状态 `pending`/`confirmed`/`handling`）兜底——
唯一约束冲突时评估器自动回退为查询已存在事件并走更新路径，不会导致评估任务失败。

## 告警通知

### 通知渠道一览

规则级 `notification_config`（JSON）只保留渠道开关（可同时开启多个）；URL 类渠道的地址/密钥在「系统管理 → 通知通道」全局统一维护（表 `alert_notify_channel_config`，每通道一行，`config` JSONB 存 `urls`/`secret`）：

| 渠道 | 规则开关字段 | 全局参数（通知通道菜单维护） | 说明 |
| --- | --- | --- | --- |
| Webhook | `channelWebhook` | `urls`（多地址） | 自定义 HTTP 回调，POST JSON |
| 邮件 | `channelEmail` | 无（SMTP 参数见下，部署配置维护） | 收件人自动解析，见下 |
| 短信 | `channelSms` | 无（阿里云参数见下，部署配置维护） | 收件人自动解析，见下 |
| 钉钉机器人 | `channelDingtalk` | `urls`、`secret`（可选，加签密钥） | 加签模式自动附加 `timestamp&sign` |
| 企业微信机器人 | `channelWecom` | `urls` | 企业微信群机器人 Webhook |
| 飞书机器人 | `channelFeishu` | `urls`、`secret`（可选，签名密钥） | 加签模式自动附加 `timestamp&sign` |

其余通用字段：`notifyOnTrigger`/`notifyOnRecovery`（bool，是否在触发/恢复时通知，默认 `true`）、`silencePeriod`（分钟，重复通知静默期，未配置时取 `alert.notify.default-silence-period-minutes`）。

- 全局通道有 `enabled` 总开关：关闭后即使规则勾选了该通道也不发送（打日志跳过）；通道未配置地址同样跳过。
- 邮件和短信目标由告警事件所属实例解析：实例负责人 A/B、实例所属分组负责人、分组成员。若联系人未配置邮箱或手机号，仅打印日志并跳过对应通道，不使用全局默认兜底目标。
- 钉钉/飞书机器人签名密钥落库前使用 `PasswordCipher`（AES-256-GCM，同采集账号密码复用同一套加解密）加密，避免明文密钥出现在 `alert_notify_channel_config.config` 与 `alert_notify_record.payload`。

### 邮件通道（SMTP）配置

邮件发送基于 `spring-boot-starter-mail` 自动装配的 `JavaMailSender`，**仅当 `spring.mail.host` 存在时才会创建该 Bean**；未配置时邮件渠道即使规则里开启也会静默跳过（打日志，不报错）。需要在 `monitor-collector` 的 `application.yml`（或环境变量）中补充：

```yaml
spring:
  mail:
    host: ${SMTP_HOST:smtp.example.com}
    port: ${SMTP_PORT:465}
    username: ${SMTP_USERNAME:alert@example.com}
    password: ${SMTP_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true          # 465 端口一般走 SSL；587 端口改用 starttls.enable=true 且 ssl.enable=false
        connectiontimeout: 5000
        timeout: 5000

alert:
  notify:
    email:
      enabled: true                          # 总开关；关闭后即使规则勾选了邮件渠道也不会发送
      provider: default
      from: ${SMTP_FROM:alert@example.com}   # 发件人，未配置时回退为 spring.mail.username
```

常见 SMTP 服务商参考：企业邮箱一般用 465(SSL) 或 587(STARTTLS)；阿里云邮件推送、腾讯企业邮箱等同理，具体端口/加密方式以服务商文档为准。

### 短信通道配置

当前内置阿里云短信 provider，通过 `alert.notify.sms.*` 配置：

```yaml
alert:
  notify:
    sms:
      enabled: true
      provider: aliyun
      aliyun:
        access-key-id: ${ALERT_ALIYUN_ACCESS_KEY_ID:}
        access-key-secret: ${ALERT_ALIYUN_ACCESS_KEY_SECRET:}
        endpoint: dysmsapi.aliyuncs.com
        sign-name: ${ALERT_ALIYUN_SMS_SIGN_NAME:}          # 短信签名，需在阿里云控制台预先审核通过
        template-code: ${ALERT_ALIYUN_SMS_TEMPLATE_CODE:}  # 短信模板，需包含 ${content} 等模板变量
```

### 通知语义说明（"已派发" ≠ "已送达"）

`AlertNotificationService.notifyOnTrigger`/`notifyOnRecovery` 返回 `true` **仅表示通知已成功派发**：通知记录已落库为 `pending` 并提交到异步发送线程池/重试队列，**不代表下游真正送达成功**（真实网络请求异步执行，可能失败）。告警事件上的 `lastNotifyTime`/`notifyCount` 同样是"已派发"口径，用于驱动"重复通知静默期"（`silencePeriod`）判断，不是"确认送达"计数。

若需要核实某条通知是否真正送达，应查询 `alert_notify_record` 表（`POST /alert/events/{id}/notify-records`），依据 `status` 字段判断：

| status | 含义 |
| --- | --- |
| `pending` | 已落库，等待异步发送 |
| `sending` | 已被某个执行节点认领并正在发送（超过 `stale-sending-minutes` 未更新视为认领方崩溃，可被重新认领） |
| `success` | 已确认发送成功（HTTP 2xx / 邮件短信 API 返回成功） |
| `failed` | 发送失败，等待按 `retry.backoff-seconds` 退避重试，超过 `retry.max-retry` 后转为 `dead` |
| `dead` | 重试次数耗尽，需人工介入排查 |

### 通知记录与重试

所有通知写入 `alert_notify_record`，失败后由 `alertNotifyRetryJobHandler` 按 `next_retry_time` 重试。
