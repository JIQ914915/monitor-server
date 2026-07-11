package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 告警通知发送记录。 */
@Data
@TableName("alert_notify_record")
public class AlertNotifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;
    private String eventCode;
    private String ruleCode;
    private String notifyKind;
    private String channel;
    private String provider;
    private String target;
    private String payload;
    private String status;
    private String responseCode;
    private String responseBody;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetry;
    private OffsetDateTime nextRetryTime;
    private OffsetDateTime sentAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
