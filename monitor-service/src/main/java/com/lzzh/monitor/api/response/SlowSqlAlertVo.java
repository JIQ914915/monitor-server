package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 慢 SQL 相关告警事件 VO（依赖慢查询指标的规则触发的事件）。 */
@Data
@Schema(description = "慢SQL相关告警事件")
public class SlowSqlAlertVo {

    @Schema(description = "事件 ID")
    private Long id;

    @Schema(description = "事件编码")
    private String eventCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "规则级别：字典 alert_level")
    private String ruleLevel;

    @Schema(description = "事件状态：字典 alert_event_status")
    private String status;

    @Schema(description = "触发值")
    private String triggerValue;

    @Schema(description = "阈值")
    private String thresholdValue;

    @Schema(description = "告警信息（模板渲染快照）")
    private String alertMessage;

    @Schema(description = "对象维度键（digest 级告警时为 SQL 指纹，实例级为空）")
    private String dimensionKey;

    @Schema(description = "首次触发时间（毫秒时间戳）")
    private Long triggerTime;

    @Schema(description = "恢复时间（毫秒时间戳，未恢复为 null）")
    private Long recoveryTime;
}
