package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 单个 SQL 指纹趋势查询入参（详情弹窗趋势图）。 */
@Data
@Schema(description = "单指纹趋势查询入参")
public class SlowSqlDigestTrendRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "库名（可空，与列表行中的 schemaName 保持一致）")
    private String schemaName;

    @Schema(description = "SQL 指纹（performance_schema digest）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "digest 不能为空")
    private String digest;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-7d", example = "1751000000000")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now", example = "1751586400000")
    private Long to;
}
