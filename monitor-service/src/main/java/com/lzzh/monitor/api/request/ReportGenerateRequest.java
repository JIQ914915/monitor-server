package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 报告生成入参（立即生成，§11.9）。 */
@Data
@Schema(description = "报告生成入参")
public class ReportGenerateRequest {

    @Schema(description = "报告类型（字典 report_type）：inspection/performance/alert", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "报告类型不能为空")
    private String reportType;

    @Schema(description = "巡检周期（字典 report_cycle，仅巡检报告）：daily/weekly/monthly/special")
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

    @Schema(description = "告警事件 ID（reportType=event 时必填，其余类型忽略）")
    private Long eventId;
}
