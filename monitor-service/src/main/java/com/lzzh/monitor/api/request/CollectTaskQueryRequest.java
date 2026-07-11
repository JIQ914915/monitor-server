package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 采集任务列表查询请求。 */
@Data
@Schema(description = "采集任务列表查询请求")
public class CollectTaskQueryRequest {

    @Schema(description = "数据库类型过滤（如 mysql），不传则不限")
    private String dbType;

    @Schema(description = "采集频率过滤：1m/1h/1d，不传则不限")
    private String frequency;

    @Schema(description = "任务状态过滤：running/error/stopped，不传则不限")
    private String status;
}
