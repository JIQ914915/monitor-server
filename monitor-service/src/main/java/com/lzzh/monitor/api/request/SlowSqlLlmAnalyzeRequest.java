package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 慢 SQL 指纹 LLM 智能分析入参（明细弹窗「智能分析」）。 */
@Data
@Schema(description = "慢SQL指纹智能分析入参")
public class SlowSqlLlmAnalyzeRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "SQL 指纹", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "digest 不能为空")
    private String digest;

    @Schema(description = "库名（可空）")
    private String schemaName;

    @Schema(description = "样本真实 SQL（可空，优先于指纹文本发送，脱敏后参与分析）")
    private String sqlText;

    @Schema(description = "统计窗口开始（毫秒时间戳）；不传默认 now-24h")
    private Long from;

    @Schema(description = "统计窗口结束（毫秒时间戳）；不传默认 now")
    private Long to;
}
