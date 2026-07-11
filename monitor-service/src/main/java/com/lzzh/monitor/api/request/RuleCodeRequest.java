package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 按规则编码操作的通用请求体。 */
@Data
@Schema(description = "规则编码请求")
public class RuleCodeRequest {

    @NotBlank(message = "规则编码不能为空")
    @Schema(description = "规则编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ruleCode;
}
