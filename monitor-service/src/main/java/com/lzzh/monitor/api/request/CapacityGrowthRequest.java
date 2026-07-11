package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 实例容量增长趋势查询入参。 */
@Data
@Schema(description = "实例容量增长趋势查询入参")
public class CapacityGrowthRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "查询天数（1~90，默认 30）", example = "30")
    @Min(value = 1, message = "days 最小为 1")
    @Max(value = 90, message = "days 最大为 90")
    private int days = 30;
}
