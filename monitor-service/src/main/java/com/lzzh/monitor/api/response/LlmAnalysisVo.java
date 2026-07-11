package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 告警事件 LLM 智能分析结果（AI 生成，仅供参考，处置须人工确认）。 */
@Data
@Schema(description = "告警事件LLM智能分析结果")
public class LlmAnalysisVo {

    @Schema(description = "事件 ID")
    private Long eventId;

    @Schema(description = "是否生成成功")
    private Boolean success;

    @Schema(description = "事件总结（AI 生成）")
    private String summary;

    @Schema(description = "可能原因（AI 生成）")
    private List<String> causes;

    @Schema(description = "处理建议（AI 生成，仅供参考，须人工确认后执行）")
    private List<String> suggestions;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    @Schema(description = "生成人")
    private String operatorName;

    @Schema(description = "生成时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;
}
