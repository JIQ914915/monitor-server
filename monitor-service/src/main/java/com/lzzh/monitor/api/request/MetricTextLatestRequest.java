package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 文本指标最新值批量查询入参。 */
@Data
@Schema(description = "文本指标最新值批量查询入参")
public class MetricTextLatestRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "指标编码列表，如 [\"mysql.var_text.sql_mode\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "codes 不能为空")
    private List<String> codes;

    @Schema(description = "频率档位：1m（分钟表，30分钟新鲜窗口）、1h（小时表，2小时新鲜窗口）或 1d（天表，2天新鲜窗口），默认 1d")
    private String frequency = "1d";
}
