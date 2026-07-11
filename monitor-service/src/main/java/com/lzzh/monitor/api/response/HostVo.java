package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/** 主机信息（响应，不含 SSH 凭据）。 */
@Data
@Schema(description = "主机信息（响应）")
public class HostVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "主机业务编码")
    private String hostCode;

    @Schema(description = "主机名称", example = "生产库主机-01")
    private String name;

    @Schema(description = "主机 IP/域名", example = "10.0.0.11")
    private String ip;

    @Schema(description = "操作系统类型", example = "linux")
    private String osType;

    @Schema(description = "采集方式：exporter / ssh / none", example = "exporter")
    private String collectMode;

    @Schema(description = "exporter 端口", example = "9100")
    private Integer exporterPort;

    @Schema(description = "exporter 指标路径", example = "/metrics")
    private String exporterPath;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：normal / abnormal / paused", example = "normal")
    private String status;

    @Schema(description = "关联实例数", example = "2")
    private Integer instanceCount;

    @Schema(description = "创建时间", example = "2026-07-08 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-08 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
