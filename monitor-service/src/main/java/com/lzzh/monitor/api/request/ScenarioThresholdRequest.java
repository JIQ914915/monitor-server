package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/** 场景实例级阈值调整请求。 */
@Data
@Schema(description = "场景实例级阈值调整请求")
public class ScenarioThresholdRequest {

    @NotNull(message = "实例 ID 不能为空")
    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long instanceId;

    @NotBlank(message = "场景编码不能为空")
    @Schema(description = "场景编码", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "scenario.connection_pool_exhaustion")
    private String scenarioCode;

    @Schema(description = "阈值覆盖：{信号code: 阈值}；与模板默认值相同的信号可不传，传空 map 表示恢复全部默认",
            example = "{\"conn_usage\": 90, \"threads_running\": 80}")
    private Map<String, Double> overrides;
}
