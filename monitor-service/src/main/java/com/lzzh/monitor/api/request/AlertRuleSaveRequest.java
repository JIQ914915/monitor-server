package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/** 告警规则新建 / 更新请求。 */
@Data
@Schema(description = "告警规则保存请求")
public class AlertRuleSaveRequest {

    @Schema(description = "规则 ID（新建时不传，更新时必传）")
    private Long id;

    @Schema(description = "规则编码（更新自定义规则时推荐传）")
    private String ruleCode;

    @Schema(description = "当前实例 ID（新建自定义规则时必传，用于绑定实例）")
    private Long instanceId;

    @NotBlank(message = "规则名称不能为空")
    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleName;

    @Schema(description = "规则类型（builtin/custom），新建时固定为 custom")
    private String ruleType;

    @NotNull(message = "告警级别不能为空")
    @Schema(description = "告警级别（level_1/level_2/level_3/level_4，一级最高）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleLevel;

    @Schema(description = "监控指标名称")
    private String metricName;

    @Schema(description = "依赖指标编码列表（可用于多指标规则）")
    private List<String> metricCodes;

    @Schema(description = "规则描述")
    private String description;

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

    /* ---- 恢复条件 ---- */

    @Schema(description = "恢复运算符")
    private String recoveryOperator;

    @Schema(description = "恢复阈值")
    private Double recoveryThreshold;

    @Schema(description = "恢复确认窗口（秒）")
    private Integer recoveryDuration;

    /* ---- 通知设置 ---- */

    @Schema(description = "触发时通知")
    private Boolean notifyOnTrigger;

    @Schema(description = "恢复时通知")
    private Boolean notifyOnRecovery;

    @Schema(description = "邮件通知")
    private Boolean channelEmail;

    @Schema(description = "短信通知")
    private Boolean channelSms;

    @Schema(description = "Webhook 通知（地址在「系统管理→通知通道」全局配置）")
    private Boolean channelWebhook;

    @Schema(description = "钉钉机器人通知（地址/密钥在「系统管理→通知通道」全局配置）")
    private Boolean channelDingtalk;

    @Schema(description = "企业微信机器人通知（地址在「系统管理→通知通道」全局配置）")
    private Boolean channelWecom;

    @Schema(description = "飞书机器人通知（地址/密钥在「系统管理→通知通道」全局配置）")
    private Boolean channelFeishu;

    @Schema(description = "静默期（分钟）")
    private Integer silencePeriod;

    /* ---- 自定义 SQL（custom 类型专用） ---- */

    @Schema(description = "自定义 SQL 查询语句")
    private String customSql;

    @Schema(description = "结果模式：single / multi")
    private String resultMode;

    @Schema(description = "单值模式：返回值字段名")
    private String sqlReturnField;

    @Schema(description = "多行模式：实体标识列名")
    private String entityColumn;

    @Schema(description = "多行模式：数值列名")
    private String valueColumn;

    @Schema(description = "告警消息模板（仅自定义规则 multi 模式可配置）")
    private String displayTemplate;

    /* ---- 其他 ---- */

    @Schema(description = "是否启用")
    private Boolean enabled;
}
