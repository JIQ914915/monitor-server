package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通用主键入参：统一以 POST body 传递 id，
 * 禁止使用 @PathVariable/@RequestParam 直接接收零散参数。
 */
@Data
@Schema(description = "通用主键入参")
public class IdRequest {

    @Schema(description = "主键 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;
}
