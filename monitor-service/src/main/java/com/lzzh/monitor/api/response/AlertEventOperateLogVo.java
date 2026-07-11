package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 告警事件处置操作流水 VO。 */
@Data
@Schema(description = "告警事件处置操作流水")
public class AlertEventOperateLogVo {

    private Long id;

    private Long eventId;

    @Schema(description = "操作类型：confirm/handling/silence/close")
    private String operateType;

    @Schema(description = "流转前状态")
    private String fromStatus;

    @Schema(description = "流转后状态")
    private String toStatus;

    @Schema(description = "操作人用户ID")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "处置备注")
    private String remark;

    @Schema(description = "操作时间", example = "2026-07-04 21:30:00")
    private String createdAt;
}
