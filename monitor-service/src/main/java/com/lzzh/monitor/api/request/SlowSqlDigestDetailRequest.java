package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 单指纹时间窗口聚合详情查询入参（详情弹窗）。 */
@Data
@Schema(description = "单指纹窗口聚合详情查询入参")
public class SlowSqlDigestDetailRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "库名（可空，与列表行的 schemaName 保持一致）")
    private String schemaName;

    @Schema(description = "SQL 指纹", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "digest 不能为空")
    private String digest;

    @Schema(description = "开始时间（毫秒时间戳，含）；不传默认为 now-24h")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳，含）；不传默认为 now")
    private Long to;
}
