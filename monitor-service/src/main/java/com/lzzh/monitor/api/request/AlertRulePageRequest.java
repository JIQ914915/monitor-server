package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 告警规则分页查询请求。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "告警规则分页查询请求")
public class AlertRulePageRequest extends PageParam {

    @Schema(description = "当前实例 ID（必传）：用于过滤该实例适用的内置规则（按 db_type）和该实例的自定义规则")
    private Long instanceId;

    @Schema(description = "关键词（模糊匹配规则名称），不传则不限")
    private String keyword;

    @Schema(description = "告警级别（level_1/level_2/level_3/level_4），不传则不限")
    private String ruleLevel;

    @Schema(description = "启用状态（true/false），不传则不限")
    private Boolean enabled;
}
