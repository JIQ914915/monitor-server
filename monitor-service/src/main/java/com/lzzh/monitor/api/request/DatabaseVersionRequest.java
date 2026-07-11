package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 数据库版本新增/修改入参（§5.8）。 */
@Data
@Schema(description = "数据库版本新增/修改入参")
public class DatabaseVersionRequest {

    @Schema(description = "主键ID（修改时必填）")
    private Long id;

    @Schema(description = "数据库类型（如 mysql），大小写不敏感", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据库类型不能为空")
    private String dbType;

    @Schema(description = "版本编码（同类型下唯一），如 8.0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "版本编码不能为空")
    private String versionCode;

    @Schema(description = "版本名称，如 MySQL 8.0")
    private String versionName;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "说明")
    private String description;
}
