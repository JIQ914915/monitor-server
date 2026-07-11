package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** LLM 智能分析配置保存请求。 */
@Data
@Schema(description = "LLM智能分析配置保存请求")
public class LlmConfigSaveRequest {

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "OpenAI 兼容接口地址（到 /v1 为止，如 http://ollama.internal:11434/v1）")
    private String baseUrl;

    @Schema(description = "API Key（传掩码 ****** 表示不修改；本地部署可留空）")
    private String apiKey;

    @Schema(description = "模型名称（如 qwen2.5:14b / gpt-4o-mini）")
    private String model;

    @Schema(description = "调用超时（秒）")
    private Integer timeoutSeconds;

    @Schema(description = "允许调用公网服务（false=数据不出域，仅允许内网/本机地址）")
    private Boolean allowExternal;

    @Schema(description = "发送前对上下文脱敏（IP 打码、SQL 字面量参数化）")
    private Boolean desensitize;
}
