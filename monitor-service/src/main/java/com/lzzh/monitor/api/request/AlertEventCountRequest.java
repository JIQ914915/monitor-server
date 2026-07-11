package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 告警事件数量查询请求。 */
@Data
@Schema(description = "告警事件数量查询请求")
public class AlertEventCountRequest {

    @Schema(description = "实例 ID（不传则查全部有权限实例）")
    private Long instanceId;

    @Schema(description = "规则级别过滤（level_1/level_2/level_3/level_4），不传则不限")
    private String ruleLevel;

    @Schema(description = "事件状态过滤，不传默认查未恢复事件（pending/confirmed/handling）",
            example = "[\"pending\",\"confirmed\",\"handling\"]")
    private List<String> statuses;

    @Schema(description = "规则类型过滤（builtin/custom），不传则不限")
    private String ruleType;
}
