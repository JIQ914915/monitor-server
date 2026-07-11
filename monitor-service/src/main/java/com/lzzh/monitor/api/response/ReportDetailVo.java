package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/** 报告详情（列表项 + 正文分段内容）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报告详情")
public class ReportDetailVo extends ReportVo {

    @Schema(description = "报告正文 {\"sections\":[{title,type(summary/table/list),summary,kv,columns,rows,items}]}")
    private Map<String, Object> content;
}
