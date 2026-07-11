package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 定时报告任务列表项。 */
@Data
@Schema(description = "定时报告任务列表项")
public class ReportScheduleVo {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "报告类型（字典 report_type）")
    private String reportType;

    @Schema(description = "巡检周期（字典 report_cycle，仅巡检报告）")
    private String cycle;

    @Schema(description = "范围方式：instance/group/owner")
    private String scopeType;

    @Schema(description = "范围描述")
    private String scopeText;

    @Schema(description = "实例 ID 列表")
    private List<Long> instanceIds;

    @Schema(description = "时间范围（字典 report_time_range）")
    private String timeRange;

    @Schema(description = "执行频率（字典 report_frequency）")
    private String frequency;

    @Schema(description = "执行时间 HH:mm")
    private String runTime;

    @Schema(description = "报告生成后推送的收件邮箱列表")
    private List<String> notifyEmails;

    @Schema(description = "下次执行时间（yyyy-MM-dd HH:mm:ss）")
    private String nextRun;

    @Schema(description = "最近执行时间（yyyy-MM-dd HH:mm:ss）")
    private String lastRunTime;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "创建人")
    private String createdBy;
}
