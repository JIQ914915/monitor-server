package com.lzzh.monitor.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 操作日志分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "操作日志分页查询请求")
public class OperLogPageRequest extends PageParam {

    @Schema(description = "操作人用户名", example = "admin")
    private String username;

    @Schema(description = "操作动作", example = "新增")
    private String action;

    @Schema(description = "所属模块", example = "实例管理")
    private String module;

    @Schema(description = "开始时间（含）", example = "2026-07-01 00:00:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @Schema(description = "结束时间（含）", example = "2026-07-02 23:59:59")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
