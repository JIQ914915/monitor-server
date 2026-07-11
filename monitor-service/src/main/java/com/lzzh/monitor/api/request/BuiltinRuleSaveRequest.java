package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 内置规则模板保存请求（系统设置 → 内置规则管理）。
 * <p>与实例级 {@link AlertRuleSaveRequest} 不同：本请求直接维护 alert_rule 模板表，
 * 携带适用类型/版本，支持产品库指标与目标库 SQL 两种数据来源。
 */
@Data
@Schema(description = "内置规则模板保存请求")
public class BuiltinRuleSaveRequest {

    @Schema(description = "模板 ID（新建不传，更新必传）")
    private Long id;

    @Schema(description = "规则编码（新建必传且全局唯一，更新不可修改）")
    private String ruleCode;

    @NotBlank(message = "规则名称不能为空")
    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleName;

    @NotBlank(message = "告警级别不能为空")
    @Schema(description = "告警级别（alert_level 字典值）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleLevel;

    @Schema(description = "适用数据库类型 ID（FK → database_type.id），必填")
    private Long dbTypeId;

    @Schema(description = "适用版本 ID 列表（FK → database_version.id），空表示该类型所有版本")
    private List<Long> dbVersionIds;

    @Schema(description = "规则描述")
    private String description;

    @Schema(description = "数据来源（alert_rule_data_source 字典值：metric 产品库指标 / target_sql 目标库 SQL）")
    private String dataSource;

    @Schema(description = "监控指标编码（metric 模式必填）")
    private String metricName;

    @Schema(description = "依赖指标编码列表（可选，多指标规则）")
    private List<String> metricCodes;

    @Schema(description = "扫描间隔（分钟）")
    private Integer scanIntervalMin;

    /* ---- 触发条件 ---- */

    @Schema(description = "比较运算符：>/>=/</<=/=/!=")
    private String operator;

    @Schema(description = "触发阈值")
    private Double threshold;

    @Schema(description = "指标单位")
    private String unit;

    @Schema(description = "持续时间（秒），0 = 立即触发")
    private Integer duration;

    /* ---- 目标库 SQL（target_sql 模式） ---- */

    @Schema(description = "目标库 SQL 查询语句（target_sql 模式必填，仅允许只读 SELECT）")
    private String customSql;

    @Schema(description = "结果模式：single / multi")
    private String resultMode;

    @Schema(description = "单值模式：返回值字段名")
    private String sqlReturnField;

    @Schema(description = "多行模式：实体标识列名")
    private String entityColumn;

    @Schema(description = "多行模式：数值列名")
    private String valueColumn;

    @Schema(description = "告警消息模板（multi 模式）")
    private String displayTemplate;

    /* ---- 布尔型（状态类）规则 ---- */

    @Schema(description = "布尔型规则：指标为 0/1 状态值，实例侧锁定条件编辑，页面按状态化文案展示")
    private Boolean booleanCondition;

    @Schema(description = "触发条件友好描述（布尔型规则用于列表/消息展示，如「检测到复制 IO 线程停止时立即触发」）")
    private String conditionDisplay;

    @Schema(description = "恢复条件友好描述（布尔型规则）")
    private String recoveryDisplay;

    /* ---- 恢复条件 ---- */

    @Schema(description = "恢复运算符")
    private String recoveryOperator;

    @Schema(description = "恢复阈值")
    private Double recoveryThreshold;

    @Schema(description = "恢复确认窗口（秒）")
    private Integer recoveryDuration;

    /* ---- 通知默认值 ---- */

    @Schema(description = "触发时通知")
    private Boolean notifyOnTrigger;

    @Schema(description = "恢复时通知")
    private Boolean notifyOnRecovery;

    @Schema(description = "邮件通知")
    private Boolean channelEmail;

    @Schema(description = "短信通知")
    private Boolean channelSms;

    @Schema(description = "Webhook 通知")
    private Boolean channelWebhook;

    @Schema(description = "钉钉机器人通知")
    private Boolean channelDingtalk;

    @Schema(description = "企业微信机器人通知")
    private Boolean channelWecom;

    @Schema(description = "飞书机器人通知")
    private Boolean channelFeishu;

    @Schema(description = "静默期（分钟）")
    private Integer silencePeriod;
}
