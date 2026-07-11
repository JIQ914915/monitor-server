package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 主机磁盘 IO 按盘趋势查询入参。 */
@Data
@Schema(description = "主机磁盘 IO 按盘趋势查询入参")
public class HostDiskIoTrendRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "起始时间（毫秒时间戳），不传默认最近 24 小时")
    private Long from;

    @Schema(description = "结束时间（毫秒时间戳），不传默认当前时间")
    private Long to;
}
