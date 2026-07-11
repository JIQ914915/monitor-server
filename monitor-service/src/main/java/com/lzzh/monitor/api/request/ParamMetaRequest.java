package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 配置参数元数据查询入参。 */
@Data
@Schema(description = "配置参数元数据查询入参")
public class ParamMetaRequest {

    @Schema(description = "按分类过滤（connection / innodb / logging / general），不传则返回全量")
    private String category;
}
