package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 采集历史日志分页查询请求。 */
@Data
@Schema(description = "采集历史日志分页查询请求")
public class CollectLogQueryRequest {

    @Schema(description = "实例 ID；查询数据库实例采集日志时传入")
    private Long instanceId;

    @Schema(description = "主机 ID；查询主机采集日志时传入")
    private Long hostId;

    @NotBlank
    @Schema(description = "采集频率：1m/1h/1d")
    private String frequency;

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数，最大 100", example = "20")
    private Integer pageSize;
}