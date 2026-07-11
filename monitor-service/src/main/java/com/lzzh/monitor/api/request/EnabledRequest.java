package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 用户启停入参，统一 POST body 传参。 */
@Data
@Schema(description = "用户启停入参，统一 POST body 传参")
public class EnabledRequest {

    @Schema(description = "主键 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "是否启用：true 启用 / false 停用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
