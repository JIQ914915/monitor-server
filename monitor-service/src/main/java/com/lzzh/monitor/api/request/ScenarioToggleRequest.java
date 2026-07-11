package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 场景启停请求。 */
@Data
@Schema(description = "场景启停请求")
public class ScenarioToggleRequest {

    @NotNull(message = "实例 ID 不能为空")
    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long instanceId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "scenario.connection_pool_exhaustion")
    private String scenarioCode;

    @NotNull(message = "启停状态不能为空")
    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
}
