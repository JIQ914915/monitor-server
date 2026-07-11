package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 告警规则（§21.2.2 + §11.5）。 */
@Data
@TableName(value = "alert_rule", autoResultMap = true)
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称。 */
    private String ruleName;

    /** 规则编码（唯一）。 */
    private String ruleCode;

    /** 运行态规则类型（由实例配置表承载，非持久列）。 */
    @TableField(exist = false)
    private String ruleType;

    /** 规则级别：level_1/level_2/level_3/level_4（一级最高）。 */
    private String ruleLevel;

    /** 适用数据库类型（FK → database_type.id），内置规则必填，自定义规则可空。 */
    private Long dbTypeId;

    /**
     * 适用版本 ID 列表（FK → database_version.id），JSONB 数组存储。
     * null 表示适用该类型所有版本。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> dbVersionIds;

    /** 规则描述。 */
    private String description;

    /** 指标编码/名称。 */
    private String metricName;

    /** 条件配置（单指标阈值 + duration 持续窗口，或自定义 SQL），jsonb。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> conditionConfig;

    /** 恢复条件配置，jsonb。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> recoveryConfig;

    /** 通知配置，jsonb。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> notificationConfig;

    /** 扫描间隔（分钟）。 */
    private Integer scanIntervalMin;

    /** 扫描间隔来源：SYSTEM_DEFAULT / USER_OVERRIDE。 */
    private String scanIntervalSource;

    /** 系统推荐的常用规则（「一键开启常用」圈选范围）。 */
    private Boolean recommended;

    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
