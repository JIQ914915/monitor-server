package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 慢 SQL 指纹聚类分页查询入参。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "慢SQL指纹聚类分页查询入参")
public class SlowSqlClusterPageRequest extends SlowSqlWindowRequest {

    @Schema(description = "页码（从 1 开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private Integer pageSize = 10;
}
