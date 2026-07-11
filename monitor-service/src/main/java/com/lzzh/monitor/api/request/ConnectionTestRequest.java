package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 实例连接测试入参。 */
@Data
@Schema(description = "实例连接测试入参")
public class ConnectionTestRequest {

    @Schema(description = "数据库类型", example = "mysql", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    @Schema(description = "主机地址", example = "127.0.0.1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "主机地址不能为空")
    private String host;

    @Schema(description = "端口", example = "3306", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "端口不能为空")
    private Integer port;

    @Schema(description = "监控库名（PostgreSQL 必须指定连接库，缺省 postgres）", example = "postgres")
    private String databaseName;

    @Schema(description = "采集账号", example = "monitor")
    private String connUser;

    @Schema(description = "采集密码")
    private String connPassword;
}
