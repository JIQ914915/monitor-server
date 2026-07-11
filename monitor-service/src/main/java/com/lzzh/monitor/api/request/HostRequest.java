package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 主机新增/编辑（请求）。 */
@Data
@Schema(description = "主机新增/编辑入参")
public class HostRequest {

    @Schema(description = "主键 ID，新增时为空，编辑时必填", example = "1")
    private Long id;

    @Schema(description = "主机名称", example = "生产库主机-01")
    private String name;

    @Schema(description = "主机 IP/域名", example = "10.0.0.11")
    private String ip;

    @Schema(description = "操作系统类型：linux / windows（字典 host_os_type）", example = "linux")
    private String osType;

    @Schema(description = "采集方式：exporter / ssh / none（字典 host_collect_mode）", example = "exporter")
    private String collectMode;

    @Schema(description = "exporter 端口", example = "9100")
    private Integer exporterPort;

    @Schema(description = "exporter 指标路径", example = "/metrics")
    private String exporterPath;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：normal / abnormal / paused（字典 host_status）", example = "normal")
    private String status;
}
