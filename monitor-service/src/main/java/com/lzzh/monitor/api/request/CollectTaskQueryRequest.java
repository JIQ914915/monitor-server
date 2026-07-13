package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 采集任务列表分页查询请求。 */
@Data
@Schema(description = "采集任务列表分页查询请求")
public class CollectTaskQueryRequest {

    @Schema(description = "实例/主机名称或地址关键字")
    private String keyword;

    @Schema(description = "数据库类型过滤（如 mysql），host 表示主机")
    private String dbType;

    @Schema(description = "采集频率过滤：1m/1h/1d，不传则不限")
    private String frequency;

    @Schema(description = "任务状态过滤：running/error/stopped，不传则不限")
    private String status;

    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer pageNum;

    @Schema(description = "每页条数，最大 100", example = "20")
    private Integer pageSize;
}