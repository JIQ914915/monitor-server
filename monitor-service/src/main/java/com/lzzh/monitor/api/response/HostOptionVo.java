package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 主机下拉选项（实例表单「所在主机」选择用）。 */
@Data
@Schema(description = "主机下拉选项")
public class HostOptionVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "主机名称", example = "生产库主机-01")
    private String name;

    @Schema(description = "主机 IP/域名", example = "10.0.0.11")
    private String ip;

    @Schema(description = "操作系统类型：linux / windows", example = "linux")
    private String osType;
}
