package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据库版本列表查询入参。 */
@Data
@Schema(description = "数据库版本列表查询入参")
public class DatabaseVersionListRequest {

    @Schema(description = "数据库类型（如 mysql），留空返回全部")
    private String dbType;
}
