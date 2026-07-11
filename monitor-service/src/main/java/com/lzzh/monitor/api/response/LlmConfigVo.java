package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** LLM 智能分析配置（api_key 只回显掩码，从不回显真实值）。 */
@Data
@Schema(description = "LLM智能分析配置")
public class LlmConfigVo {

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "OpenAI 兼容接口地址")
    private String baseUrl;

    @Schema(description = "API Key 掩码（已配置=******，未配置=空）")
    private String apiKey;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "调用超时（秒）")
    private Integer timeoutSeconds;

    @Schema(description = "允许调用公网服务（false=数据不出域）")
    private Boolean allowExternal;

    @Schema(description = "发送前对上下文脱敏")
    private Boolean desensitize;
}
