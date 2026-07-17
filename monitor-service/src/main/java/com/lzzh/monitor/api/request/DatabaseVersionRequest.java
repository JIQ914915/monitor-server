package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 数据库版本新增/修改入参。 */
@Data
@Schema(description = "数据库版本新增/修改入参")
public class DatabaseVersionRequest {
    private Long id;

    @NotNull(message = "数据库类型不能为空")
    private Long dbTypeId;

    @NotBlank(message = "版本编码不能为空")
    private String versionCode;

    private String versionName;
    private Integer sortOrder;
    private String description;
}
