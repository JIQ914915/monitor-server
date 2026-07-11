package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 监控场景 VO（列表与详情共用，详情额外返回诊断结论与知识库文章）。 */
@Data
@Schema(description = "监控场景")
public class ScenarioVo {

    @Schema(description = "场景 ID")
    private Long id;

    @Schema(description = "场景编码", example = "scenario.connection_pool_exhaustion")
    private String scenarioCode;

    @Schema(description = "场景名称", example = "连接池耗尽风险")
    private String scenarioName;

    @Schema(description = "场景说明")
    private String description;

    @Schema(description = "级别（字典 alert_level）", example = "level_2")
    private String severity;

    @Schema(description = "触发逻辑：AND=全部满足才触发 / OR=任一满足即触发", example = "AND")
    private String logic;

    @Schema(description = "触发持续时长（秒），0=立即触发", example = "120")
    private Integer duration;

    @Schema(description = "是否启用（实例级配置，默认停用）")
    private Boolean enabled;

    @Schema(description = "当前状态：triggered=触发中 / normal=正常 / disabled=已停用 / unknown=数据缺失",
            example = "normal")
    private String currentStatus;

    @Schema(description = "累计触发次数（当前实例）", example = "23")
    private Long triggerCount;

    @Schema(description = "各信号实时状态：[{code,name,expr,metricCode,currentVal,met,state,"
            + "condType,operator,threshold,defaultThreshold,unit}]，threshold 为实例级覆盖后的生效阈值")
    private List<Map<String, Object>> signals;

    @Schema(description = "诊断结论（详情返回；仅触发中时有值，为按命中信号渲染后的结论）")
    private String diagnosis;

    @Schema(description = "诊断结论模板（详情返回；供未触发时预览触发后的诊断输出）")
    private String diagnosisTemplate;

    @Schema(description = "关联知识库文章（详情返回）：[{id,title,category}]")
    private List<Map<String, Object>> knowledgeArticles;

    @Schema(description = "是否内置场景")
    private Boolean builtin;

    @Schema(description = "系统推荐的常用场景（「一键开启常用」圈选范围）")
    private Boolean recommended;
}
