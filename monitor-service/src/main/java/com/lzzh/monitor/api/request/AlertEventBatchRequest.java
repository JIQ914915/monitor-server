package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 告警事件批量操作入参。 */
@Data
@Schema(description = "告警事件批量操作入参")
public class AlertEventBatchRequest {

    @Schema(description = "事件 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "事件ID列表不能为空")
    private List<Long> ids;

    @Schema(description = "静默窗口时长（小时，仅静默操作使用）", example = "2")
    @Min(value = 1, message = "静默时长最少1小时")
    @Max(value = 720, message = "静默时长最多720小时")
    private Integer silenceHours;

    @Schema(description = "处置备注（可选，最长500字，写入事件最近备注与操作流水）")
    @jakarta.validation.constraints.Size(max = 500, message = "处置备注最长500字")
    private String remark;
}

