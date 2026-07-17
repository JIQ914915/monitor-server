package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 采集目标（内部响应）：collector 分片拉取待采实例时使用，
 * 含连接凭据但不经由对外 API 暴露，实体不越 service 层。
 */
@Data
@Schema(description = "采集目标（内部响应，含连接凭据，不对外暴露）")
public class CollectTargetVo {

    @Schema(description = "实例主键 ID", example = "1")
    private Long id;

    @Schema(description = "数据库类型稳定编码（解析自 dbTypeId）", example = "MYSQL")
    private String dbType;

    @Schema(description = "数据库类型展示名", example = "MySQL")
    private String dbTypeLabel;

    @Schema(description = "数据库版本编码（解析自 dbVersionId，供版本适配）", example = "8.0")
    private String dbVersion;

    @Schema(description = "主机地址", example = "127.0.0.1")
    private String host;

    @Schema(description = "端口", example = "3306")
    private Integer port;

    @Schema(description = "采集账号", example = "monitor")
    private String connUser;

    @Schema(description = "采集密码")
    private String connPassword;

    @Schema(description = "实例状态：normal / abnormal / paused，采集侧据此显式跳过暂停实例", example = "normal")
    private String status;

    @Schema(description = "JDBC 驱动类名（来自 database_type，供多库建连）", example = "com.mysql.cj.jdbc.Driver")
    private String driverClass;

    @Schema(description = "JDBC URL 模板（来自 database_type，{host}/{port}/{database} 占位符）",
            example = "jdbc:mysql://{host}:{port}/{database}?useSSL=false")
    private String urlTemplate;

    @Schema(description = "实例业务编码（稳定唯一，分片/引用依据）")
    private String instanceCode;

    @Schema(description = "实例名称（冗余快照，供告警等下游消费）")
    private String instanceName;

    @Schema(description = "连接目标数据库名（替换 URL 模板中的 {database} 占位符，空则为空字符串替换）",
            example = "mydb")
    private String databaseName;

    private String pgObjectScope;
    private java.util.List<String> pgObjectDatabases;

    @Schema(description = "连接来源白名单（IP 精确或 \"10.0.1.*\" 前缀通配；空 = 未启用来源检测）")
    private java.util.List<String> connSourceWhitelist;
}
