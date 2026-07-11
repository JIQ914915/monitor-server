package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 性能分析多指标趋势批量查询入参。
 * <p>一次请求返回多个指标在同一时间范围内的趋势序列，供性能分析页分类图表一次性取数，
 * 避免页面加载时发起数十个单指标请求。
 */
@Data
@Schema(description = "性能分析多指标趋势批量查询入参")
public class PerfTrendBatchRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "指标编码列表（≤40 个）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "metricCodes 不能为空")
    @Size(max = 40, message = "metricCodes 最多 40 个")
    private List<String> metricCodes;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-24h",
            example = "1751500000000")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now",
            example = "1751586400000")
    private Long to;

    @Schema(description = "数据频率：1m（分钟级）/ 1h（小时级，由 1m 降采样或原生小时采集），默认 1h",
            example = "1h", allowableValues = {"1m", "1h"})
    private String frequency;
}
