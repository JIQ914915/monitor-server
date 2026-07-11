package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 内置规则模板分页查询请求（系统设置 → 内置规则管理，全局视角）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "内置规则模板分页查询请求")
public class BuiltinRulePageRequest extends PageParam {

    @Schema(description = "关键词（模糊匹配规则名称/编码），不传则不限")
    private String keyword;

    @Schema(description = "告警级别（alert_level 字典值），不传则不限")
    private String ruleLevel;

    @Schema(description = "适用数据库类型 ID（FK → database_type.id），不传则不限")
    private Long dbTypeId;

    @Schema(description = "数据来源（alert_rule_data_source 字典值：metric/target_sql），不传则不限")
    private String dataSource;
}
