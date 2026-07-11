package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 告警触发/恢复持续窗口状态。 */
@Data
@TableName("alert_evaluate_window")
public class AlertEvaluateWindow {

    private String dedupKey;

    private String windowType;

    private OffsetDateTime firstMatchTime;

    private OffsetDateTime lastEvalTime;

    private OffsetDateTime expireTime;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
