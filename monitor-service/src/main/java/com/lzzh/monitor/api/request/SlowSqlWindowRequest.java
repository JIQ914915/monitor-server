package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 慢 SQL 时间窗口查询入参（统计卡 / 库名列表共用）。 */
@Data
@Schema(description = "慢SQL时间窗口查询入参")
public class SlowSqlWindowRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-24h", example = "1751500000000")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now", example = "1751586400000")
    private Long to;
}
