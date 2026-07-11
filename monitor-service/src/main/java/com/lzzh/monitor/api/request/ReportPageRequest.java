package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 报告归档分页查询入参。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报告归档分页查询入参")
public class ReportPageRequest extends PageParam {

    @Schema(description = "报告类型过滤（字典 report_type）")
    private String reportType;
}
