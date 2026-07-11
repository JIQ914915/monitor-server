package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 报告归档列表项（不含正文，正文经 detail 接口获取）。 */
@Data
@Schema(description = "报告归档列表项")
public class ReportVo {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "报告编码", example = "INSP-1720000000000")
    private String reportCode;

    @Schema(description = "报告名称")
    private String title;

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

    @Schema(description = "生成方式（字典 report_gen_mode）")
    private String genMode;

    @Schema(description = "状态（字典 report_status）")
    private String status;

    @Schema(description = "生成人")
    private String createdBy;

    @Schema(description = "生成时间（yyyy-MM-dd HH:mm:ss）")
    private String generateTime;
}
