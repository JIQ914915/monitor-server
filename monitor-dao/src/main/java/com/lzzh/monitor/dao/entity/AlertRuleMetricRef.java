package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 告警规则依赖指标关联（支持多指标）。 */
@Data
@TableName("alert_rule_metric_ref")
public class AlertRuleMetricRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String metricCode;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
