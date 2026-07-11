package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 数据库类型新增/修改入参（§5.8）。 */
@Data
@Schema(description = "数据库类型新增/修改入参")
public class DatabaseTypeRequest {

    @Schema(description = "主键ID（修改时必填）")
    private Long id;

    @Schema(description = "类型编码（唯一），如 MYSQL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "类型编码不能为空")
    private String code;

    @Schema(description = "展示名，如 MySQL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "展示名不能为空")
    private String label;

    @Schema(description = "JDBC 驱动类名，如 com.mysql.cj.jdbc.Driver")
    private String driverClass;

    @Schema(description = "JDBC URL 模板，如 jdbc:mysql://{host}:{port}/{database}?useSSL=false")
    private String urlTemplate;

    @Schema(description = "采集器实现类全限定名")
    private String collectorClass;

    @Schema(description = "默认端口，如 3306")
    private Integer defaultPort;

    @Schema(description = "图标URL（可选）")
    private String dbIcon;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
