package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单指标时序趋势查询入参。
 * <p>{@code from} / {@code to} 不传时默认取最近 1 小时（性能监控图表默认时间范围）。
 */
@Data
@Schema(description = "单指标时序趋势查询入参")
public class MetricTrendRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "指标编码，如 mysql.qps", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "metricCode 不能为空")
    private String metricCode;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-1h",
            example = "1751500000000")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now",
            example = "1751503600000")
    private Long to;

    @Schema(description = "数据频率：1m（分钟级，默认）/ 1h（小时级）",
            example = "1m", allowableValues = {"1m", "1h"})
    private String frequency;
}
