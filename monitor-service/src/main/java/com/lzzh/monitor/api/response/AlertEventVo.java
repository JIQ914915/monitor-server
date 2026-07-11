package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 告警事件 VO（用于实时概况「事件 Tab」未恢复事件列表）。 */
@Data
@Schema(description = "告警事件")
public class AlertEventVo {

    @Schema(description = "事件 ID")
    private Long id;

    @Schema(description = "事件编码", example = "EVT-20260703-000123")
    private String eventCode;

    @Schema(description = "规则 ID")
    private Long ruleId;

    @Schema(description = "规则名称", example = "连接使用率偏高")
    private String ruleName;

    @Schema(description = "规则级别（字典 alert_level）：level_1 / level_2 / level_3 / level_4（一级最高）",
            example = "level_3")
    private String ruleLevel;

    @Schema(description = "实例 ID")
    private Long instanceId;

    @Schema(description = "实例名称", example = "prod-mysql-01")
    private String instanceName;

    @Schema(description = "触发时的指标值", example = "92.3%")
    private String triggerValue;

    @Schema(description = "告警阈值", example = "90%")
    private String thresholdValue;

    @Schema(description = "告警信息（规则模板渲染结果）", example = "【连接使用率过高】当前值 92.3，阈值 >=80")
    private String alertMessage;

    @Schema(description = "布尔型规则事件（复制线程停止等）：触发值/阈值为 0/1 无业务含义，页面应按状态化文案展示")
    private Boolean booleanCondition;

    @Schema(description = "对象维度键（如 digest / 表名），无对象维度时为空")
    private String dimensionKey;

    @Schema(description = "触发次数（归并后）", example = "3")
    private Integer triggerCount;

    @Schema(description = "首次触发时间", example = "2026-07-03 09:00:00")
    private String triggerTime;

    @Schema(description = "最近触发时间", example = "2026-07-03 09:03:00")
    private String lastTriggerTime;

    @Schema(description = "持续时间（秒）", example = "180")
    private Long durationSeconds;

    @Schema(description = "状态：pending / confirmed / handling / recovered / closed / ignored",
            example = "pending")
    private String status;

    @Schema(description = "处理人", example = "张三")
    private String assignee;

    @Schema(description = "最近一次处置备注")
    private String lastRemark;

    @Schema(description = "确认执行人用户ID", example = "1001")
    private Long confirmUserId;

    @Schema(description = "确认执行人名称", example = "张三")
    private String confirmUserName;

    @Schema(description = "静默执行人用户ID", example = "1002")
    private Long silenceUserId;

    @Schema(description = "静默执行人名称", example = "李四")
    private String silenceUserName;

    @Schema(description = "关闭执行人用户ID", example = "1003")
    private Long closeUserId;

    @Schema(description = "关闭执行人名称", example = "王五")
    private String closeUserName;

    @Schema(description = "静默到期时间（窗口期结束后可再次建单）", example = "2026-07-04 13:30:00")
    private String silenceUntilTime;

    @Schema(description = "最近评估状态：normal / metric_missing")
    private String evalState;

    @Schema(description = "最近评估说明")
    private String evalMessage;

    @Schema(description = "最近评估时间", example = "2026-07-04 13:30:00")
    private String lastEvalTime;

    @Schema(description = "事件来源（字典 event_source）：rule=告警规则 / scenario=场景综合诊断 / system=系统事件",
            example = "scenario")
    private String eventSource;

    @Schema(description = "场景编码（仅场景综合事件）", example = "scenario.connection_pool_exhaustion")
    private String scenarioCode;

    @Schema(description = "场景事件触发时信号快照：[{code,name,expr,currentVal,met,state}]")
    private List<Map<String, Object>> signalsSnapshot;

    @Schema(description = "场景关联知识库文章（仅场景综合事件）：[{id,title}]")
    private List<Map<String, Object>> knowledgeArticles;

    @Schema(description = "阻塞链现场快照（锁相关事件建单时即席抓取）：{capturedAt,dbVersion,total,error,rows[]}")
    private Map<String, Object> blockingChainSnapshot;
}
