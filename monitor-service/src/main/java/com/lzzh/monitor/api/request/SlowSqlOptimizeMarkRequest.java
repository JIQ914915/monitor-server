package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** SQL 指纹优化状态标记入参。 */
@Data
@Schema(description = "SQL指纹优化状态标记入参")
public class SlowSqlOptimizeMarkRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "库名（可空，与列表行 schemaName 保持一致）")
    private String schemaName;

    @Schema(description = "SQL 指纹", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "digest 不能为空")
    private String digest;

    @Schema(description = "优化状态：字典 slow_sql_optimize_status（unoptimized/optimized）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "status 不能为空")
    @Pattern(regexp = "unoptimized|optimized", message = "status 仅支持 unoptimized/optimized")
    private String status;
}
