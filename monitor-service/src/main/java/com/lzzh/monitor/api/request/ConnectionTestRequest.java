package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 实例连接测试入参。 */
@Data
@Schema(description = "实例连接测试入参")
public class ConnectionTestRequest {

    @Schema(description = "已有实例ID；编辑连接测试时以服务端实例元数据为准")
    private Long instanceId;

    @Schema(description = "数据库类型ID；仅新增实例连接测试时必填")
    private Long dbTypeId;

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @NotNull(message = "端口不能为空")
    private Integer port;

    private String databaseName;
    private String connUser;
    private String connPassword;
}
