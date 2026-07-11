package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 表级周环比增长 Top N 查询入参。 */
@Data
@Schema(description = "表级周环比增长 Top N 查询入参")
public class TableGrowthRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "容量指标编码（默认 capacity.total_size_bytes）",
            example = "capacity.total_size_bytes")
    private String metricCode = "capacity.total_size_bytes";

    @Schema(description = "返回条数（1~200，默认 50）", example = "50")
    @Min(value = 1, message = "limit 最小为 1")
    @Max(value = 200, message = "limit 最大为 200")
    private int limit = 50;
}
