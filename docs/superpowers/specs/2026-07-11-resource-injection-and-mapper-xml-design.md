# Resource 注入与 Mapper 长 SQL XML 化设计

## 目标

一次性整改 `monitor-server`：生产 Spring Bean 的依赖统一使用 `jakarta.annotation.Resource` 字段注入；Mapper 中的长 SQL 迁移到 MyBatis XML，短小单行 SQL 保留注解。实施按模块拆分提交，每阶段可独立验证和回退。

## 范围

### Resource 注入

- 覆盖 `monitor-admin`、`monitor-service`、`monitor-collector` 及各采集插件中的生产 Spring Bean。
- 将依赖字段从 `private final` 构造注入改为 `@Resource private` 字段注入，并移除仅用于依赖注入的构造函数。
- 同类型存在多个 Bean 时使用 `@Resource(name = "...")` 明确 Bean 名称。
- `@Bean` 工厂方法参数、普通 Java 对象、实体、DTO、record 及非 Spring 管理类不改。
- 同步调整直接调用构造函数的单元测试，使用受控字段注入测试辅助方法；不为生产类增加测试专用入口。

### Mapper XML

- 迁移使用 Java 文本块的 SQL。
- 迁移包含 `<script>`、`<if>`、`<foreach>`、`<choose>` 等动态片段的 SQL。
- 迁移正文超过 120 个字符或跨多行的 SQL。
- 迁移 Provider 生成的复杂 SQL，并删除不再使用的 Provider 实现。
- 简单单行 CRUD SQL继续保留 Mapper 注解。
- MyBatis-Plus `BaseMapper` 自带方法不在 XML 重复声明。

## 结构

- XML 位于 `monitor-dao/src/main/resources/mapper/`，每个需要迁移的 Mapper 对应一个同名 XML。
- XML `namespace` 使用 Mapper 接口全限定名，statement `id` 与 Java 方法名一致。
- Admin 与 Collector 均显式配置 `classpath*:mapper/**/*.xml`，确保共享 DAO 资源可被两个运行时加载。
- Java 方法签名、`@Param` 名称、返回类型及调用方保持不变，只移动 SQL 定义位置。

## 实施顺序

1. 增加 XML 加载配置和结构契约测试。
2. 按 Mapper 分批迁移长 SQL，每批运行 DAO 契约测试并提交。
3. 按 `monitor-service`、`monitor-admin`、`monitor-collector`、采集插件顺序迁移 Bean 注入，同步调整对应测试并分别提交。
4. 执行全仓扫描，确认没有符合长 SQL 标准的遗漏，也没有生产 Spring Bean 保留依赖构造注入。
5. 运行模块测试和全量 Maven 测试，检查最终 diff 与工作区状态。

## 验证

- XML 契约测试解析 Mapper XML，校验 namespace、statement id 和对应接口方法。
- 源码结构测试扫描 Mapper，禁止符合长 SQL标准的映射注解继续存在。
- 源码结构测试扫描生产 Spring Bean，禁止依赖构造注入，校验依赖字段具有 `@Resource`。
- 对迁移前已有的 Mapper SQL 脚本测试改为从 MyBatis `MappedStatement` 或 XML 资源读取 SQL。
- 分模块运行 Maven 测试，最终运行根目录 `mvn test`。

## 兼容性与风险控制

- XML 中完整保留原 SQL、动态条件顺序、参数占位符和 result mapping，不顺手优化查询。
- `@Resource` 默认按字段名解析；对多实现接口、多个同类型 Bean 和集合注入逐项审计，必要时显式指定名称。
- 修改现有测试只为适配注入方式与 SQL 存放位置，不改变业务断言。
- 保留工作区中用户已有的 `InstanceServiceImpl.java` import 调整，重叠修改时以当前工作区内容为基线。
- 每个阶段独立提交；发生回归时可按模块定位和回退，不混入无关格式化或重构。
