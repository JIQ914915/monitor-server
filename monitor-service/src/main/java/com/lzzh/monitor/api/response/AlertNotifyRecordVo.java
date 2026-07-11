package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 告警通知记录 VO。 */
@Data
@Schema(description = "告警通知记录")
public class AlertNotifyRecordVo {

    private Long id;
    private Long eventId;
    private String eventCode;
    private String ruleCode;
    private String notifyKind;
    private String channel;
    private String provider;
    private String target;
    private String status;
    private String responseCode;
    private String responseBody;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private String nextRetryTime;
    private String sentAt;
    private String createdAt;
    private String updatedAt;
}
