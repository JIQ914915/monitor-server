package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 指标定义列表查询入参。 */
@Data
@Schema(description = "指标定义列表查询入参")
public class MetricDefinitionListRequest {

    @Schema(description = "数据库类型ID；留空返回全部")
    private Long dbTypeId;
}
