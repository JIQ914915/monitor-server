package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 采集历史日志查询请求。 */
@Data
@Schema(description = "采集历史日志查询请求")
public class CollectLogQueryRequest {

    @NotNull
    @Schema(description = "实例 ID")
    private Long instanceId;

    @NotBlank
    @Schema(description = "采集频率：1m/1h/1d")
    private String frequency;

    @Schema(description = "最多返回条数（默认 50，最大 200）")
    private Integer limit;
}
