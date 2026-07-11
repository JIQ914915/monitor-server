package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 告警规则视图对象（展平 JSONB 条件/通知字段，方便前端直接使用）。 */
@Data
@Schema(description = "告警规则 VO")
public class AlertRuleVo {

    private Long id;

    private String ruleName;

    private String ruleCode;

    /** builtin / custom */
    private String ruleType;

    /** 适用数据库类型 ID（FK → database_type.id） */
    private Long dbTypeId;

    /** 适用数据库类型名称（如 MySQL），由 dbTypeId 解析得到，前端展示用 */
    private String dbType;

    /**
     * 适用版本 ID 列表（FK → database_version.id 数组），null 表示该类型所有版本。
     */
    private java.util.List<Long> dbVersionIds;

    /**
     * 适用版本可读标签（逗号分隔 version_code，如 {@code 5.7,8.0}），
     * 由 dbVersionIds 解析得到，null 表示全版本。
     */
    private String dbVersion;

    /** level_1 / level_2 / level_3 / level_4（一级最高） */
    private String ruleLevel;

    /**
     * 数据来源（alert_rule_data_source 字典值）：
     * metric = 产品库指标评估；target_sql = 直连目标库执行 SQL 评估。
     * 由生效 conditionConfig 是否携带 customSql 推导，前端按字典解析展示。
     */
    private String dataSource;

    /** 已启用该规则的实例数（仅内置规则管理全局视角填充） */
    private Integer enabledInstanceCount;

    private String metricName;

    private List<String> metricCodes;

    private String description;

    /** 当前生效扫描间隔（分钟） */
    private Integer scanIntervalMin;

    /** 扫描间隔来源：SYSTEM_DEFAULT / USER_OVERRIDE */
    private String scanIntervalSource;

    /** 规则最小允许扫描间隔（分钟） */
    private Integer minAllowedIntervalMin;

    /** 规则依赖指标采样周期最大值（分钟） */
    private Integer metricSamplingMaxIntervalMin;

    /* ---- 触发条件（从 conditionConfig 展平） ---- */

    private String operator;

    private Double threshold;

    private String unit;

    /** 触发持续秒数（内置）或连续满足次数（自定义，存于 duration 字段） */
    private Integer duration;

    /**
     * 条件锁定（conditionConfig.conditionType = boolean 的布尔型规则）：
     * 触发/恢复条件由系统固化，用户仅可修改告警级别、扫描间隔、通知设置与启停。
     */
    private Boolean conditionLocked;

    /** 触发条件友好描述（conditionConfig.displayText），锁定规则用它替代 "< 1" 类机器语义展示 */
    private String conditionDisplay;

    /** 恢复条件友好描述（recoveryConfig.displayText） */
    private String recoveryDisplay;

    /* ---- 恢复条件（从 recoveryConfig 展平） ---- */

    private String recoveryOperator;

    private Double recoveryThreshold;

    private Integer recoveryDuration;

    /* ---- 通知设置（从 notificationConfig 展平） ---- */

    private Boolean notifyOnTrigger;

    private Boolean notifyOnRecovery;

    private Boolean channelEmail;

    private Boolean channelSms;

    /** Webhook 通知勾选（地址在「系统管理→通知通道」全局配置） */
    private Boolean channelWebhook;

    /** 钉钉机器人通知勾选（地址/密钥在「系统管理→通知通道」全局配置） */
    private Boolean channelDingtalk;

    /** 企业微信机器人通知勾选（地址在「系统管理→通知通道」全局配置） */
    private Boolean channelWecom;

    /** 飞书机器人通知勾选（地址/密钥在「系统管理→通知通道」全局配置） */
    private Boolean channelFeishu;

    private Integer silencePeriod;

    /* ---- 自定义 SQL（custom 类型，从 conditionConfig 展平） ---- */

    private String customSql;

    /** single / multi */
    private String resultMode;

    private String sqlReturnField;

    private String entityColumn;

    private String valueColumn;

    private String displayTemplate;

    /* ---- 统计（聚合自 alert_event） ---- */

    /** 触发总次数（当前实例维度） */
    private Integer triggerCount;

    /** 最近触发时间（当前实例维度） */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime lastTriggerAt;

    /** 系统推荐的常用规则（「一键开启常用」圈选范围，仅内置规则可能为 true） */
    private Boolean recommended;

    /* ---- 状态 ---- */

    /**
     * 当前实例的启用状态（内置/自定义规则均由 alert_rule_instance_config.enabled 决定，
     * 模板表 alert_rule 不再持有 enabled 列）。
     */
    private Boolean instanceEnabled;

    private String createdBy;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
