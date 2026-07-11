package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 慢 SQL 实时执行计划请求（连目标库执行 EXPLAIN）。 */
@Data
public class SlowSqlExplainRequest {

    @Schema(description = "实例ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "库名（EXPLAIN 时作为默认库，空则用实例配置的连接库）")
    private String schemaName;

    @Schema(description = "真实 SQL（含参数），仅支持 SELECT/INSERT/UPDATE/DELETE/REPLACE 单条语句",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "sql 不能为空")
    private String sql;
}
