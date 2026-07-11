package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 主机 exporter 连通性测试（请求）。 */
@Data
@Schema(description = "主机 exporter 连通性测试入参")
public class HostConnectionTestRequest {

    @NotBlank
    @Schema(description = "主机 IP/域名", example = "10.0.0.11")
    private String ip;

    @Schema(description = "exporter 端口，缺省 9100", example = "9100")
    private Integer exporterPort;

    @Schema(description = "exporter 指标路径，缺省 /metrics", example = "/metrics")
    private String exporterPath;

    @Schema(description = "操作系统类型：linux / windows（传入时校验 exporter 与类型是否匹配）", example = "linux")
    private String osType;
}
