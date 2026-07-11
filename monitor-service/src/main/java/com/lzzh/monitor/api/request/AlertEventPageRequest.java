package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/** 告警事件分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "告警事件分页查询请求")
public class AlertEventPageRequest extends PageParam {

    @Schema(description = "实例 ID（不传则查全部有权限实例）")
    private Long instanceId;

    @Schema(description = "规则级别过滤（level_1/level_2/level_3/level_4），不传则不限")
    private String ruleLevel;

    @Schema(description = "事件状态过滤，不传默认查未恢复事件（pending/confirmed/handling）",
            example = "[\"pending\",\"confirmed\",\"handling\"]")
    private List<String> statuses;

    @Schema(description = "规则类型过滤（builtin/custom），不传则不限")
    private String ruleType;

    @Schema(description = "事件来源过滤（字典 event_source：rule/scenario/system），不传则不限")
    private String eventSource;

    @Schema(description = "场景编码过滤（仅场景综合事件），不传则不限")
    private String scenarioCode;
}
