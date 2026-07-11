package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 多指标最新值批量查询入参。 */
@Data
@Schema(description = "多指标最新值批量查询入参")
public class MetricLatestRequest {

    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "instanceId 不能为空")
    private Long instanceId;

    @Schema(description = "指标编码列表，如 [\"mysql.qps\",\"mysql.tps\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "codes 不能为空")
    private List<String> codes;
}
