package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 对象级指标 Top N 查询入参（表空间 Top N、连接来源 Top N 等）。 */
@Data
@Schema(description = "对象级指标 Top N 查询入参")
public class MetricObjectRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "指标编码，如 capacity.total_size_bytes",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricCode 不能为空")
    private String metricCode;

    @Schema(description = "返回条数（1~200，默认 20）", example = "20")
    @Min(value = 1, message = "limit 最小为 1")
    @Max(value = 200, message = "limit 最大为 200")
    private int limit = 20;
}
