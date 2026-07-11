package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 定时报告任务保存入参（生成报告对话框选择“定时生成”时创建）。 */
@Data
@Schema(description = "定时报告任务保存入参")
public class ReportScheduleSaveRequest {

    @Schema(description = "主键 ID，为空表示新增")
    private Long id;

    @Schema(description = "报告类型（字典 report_type）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "报告类型不能为空")
    private String reportType;

    @Schema(description = "巡检周期（字典 report_cycle，仅巡检报告）")
    private String cycle;

    @Schema(description = "范围方式：instance/group/owner", example = "instance")
    private String scopeType;

    @Schema(description = "实例 ID 列表（scopeType=instance 时必填）")
    private List<Long> instanceIds;

    @Schema(description = "分组 ID 列表（scopeType=group 时必填）")
    private List<Long> groupIds;

    @Schema(description = "负责人用户 ID 列表（scopeType=owner 时必填）")
    private List<Long> ownerIds;

    @Schema(description = "时间范围（字典 report_time_range）：24h/7d/30d", example = "24h")
    private String timeRange;

    @Schema(description = "执行频率（字典 report_frequency）：daily/weekly/monthly", example = "daily")
    @NotBlank(message = "执行频率不能为空")
    private String frequency;

    @Schema(description = "执行时间 HH:mm", example = "08:30")
    @NotBlank(message = "执行时间不能为空")
    private String runTime;

    @Schema(description = "报告生成后推送的收件邮箱列表（空则不推送）")
    private List<String> notifyEmails;
}
