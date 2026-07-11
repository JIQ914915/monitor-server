package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 告警事件处置操作流水（人工流转历史）。 */
@Data
@TableName("alert_event_operate_log")
public class AlertEventOperateLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long eventId;

    private String eventCode;

    /** 操作类型：confirm/handling/silence/close/auto_recover（系统自愈）。 */
    private String operateType;

    /** 流转前状态。 */
    private String fromStatus;

    /** 流转后状态。 */
    private String toStatus;

    private Long operatorId;

    /** 操作人姓名快照。 */
    private String operatorName;

    private String remark;

    private OffsetDateTime createdAt;
}
