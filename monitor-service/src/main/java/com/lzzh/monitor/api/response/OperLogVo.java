package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志（响应）。 */
@Data
@Schema(description = "操作日志信息（响应）")
public class OperLogVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "操作时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operTime;

    @Schema(description = "操作人用户名", example = "admin")
    private String username;

    @Schema(description = "所属模块", example = "实例管理")
    private String module;

    @Schema(description = "操作动作", example = "新增")
    private String action;

    @Schema(description = "操作目标", example = "生产库-订单")
    private String target;

    @Schema(description = "操作来源 IP", example = "127.0.0.1")
    private String ip;

    @Schema(description = "是否成功", example = "true")
    private Boolean success;

    @Schema(description = "操作详情", example = "创建实例，ID=1")
    private String detail;
}
