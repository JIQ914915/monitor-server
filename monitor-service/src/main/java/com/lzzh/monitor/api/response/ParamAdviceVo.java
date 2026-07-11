package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 参数调优建议（§15.4.6）：基于已采集的配置参数 + 运行指标做规则化体检，
 * 仅输出建议与依据，任何参数调整须人工评估并走变更流程执行。
 */
@Data
@Schema(description = "参数调优建议项")
public class ParamAdviceVo {

    @Schema(description = "参数名")
    private String paramName;

    @Schema(description = "参数中文说明")
    private String displayName;

    @Schema(description = "当前值（格式化展示）")
    private String currentValue;

    @Schema(description = "观察依据（关联的运行指标表现）")
    private String observation;

    @Schema(description = "调整建议")
    private String advice;

    @Schema(description = "级别：warning=建议尽快评估 / info=提示知晓")
    private String level;
}
