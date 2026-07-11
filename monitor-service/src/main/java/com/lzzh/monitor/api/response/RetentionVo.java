package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据保留策略（响应）。 */
@Data
@Schema(description = "数据保留策略信息（响应）")
public class RetentionVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "数据类别", example = "minute")
    private String category;

    @Schema(description = "保留天数", example = "7")
    private Integer retentionDays;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}
