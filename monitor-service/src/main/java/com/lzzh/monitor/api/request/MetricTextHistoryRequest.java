package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 文本指标变更历史查询入参。 */
@Data
@Schema(description = "文本指标变更历史查询入参")
public class MetricTextHistoryRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "指标编码，如 mysql.var_text.sql_mode",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricCode 不能为空")
    private String metricCode;
}
