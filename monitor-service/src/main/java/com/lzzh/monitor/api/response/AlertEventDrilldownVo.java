package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警事件下钻分析上下文（§11.7 事件下钻）。
 *
 * <p>在事件基础信息之上补充规则与指标元数据，前端据此：
 * <ul>
 *   <li>按 metricCode 拉取告警前后 ±30 分钟的真实趋势并叠加触发标记；</li>
 *   <li>按 metricCode 匹配"告警类型画像"（关联指标 / 可能原因 / 排查路径 / 建议动作）；</li>
 *   <li>展示阈值条件（operator + thresholdValue + unit）。</li>
 * </ul>
 */
@Data
@Schema(description = "告警事件下钻分析上下文：事件 + 规则/指标元数据")
public class AlertEventDrilldownVo {

    @Schema(description = "事件基础信息（同事件列表行结构）")
    private AlertEventVo event;

    @Schema(description = "规则编码（规则已删除时为空）", example = "builtin.conn.usage")
    private String ruleCode;

    @Schema(description = "规则说明")
    private String ruleDescription;

    @Schema(description = "触发指标编码（画像匹配与趋势查询主键）", example = "mysql.conn.usage")
    private String metricCode;

    @Schema(description = "指标中文名（来自指标定义，缺失时前端回退 metricCode）", example = "连接使用率")
    private String metricLabel;

    @Schema(description = "指标单位（来自指标定义，可能为空）", example = "%")
    private String unit;

    @Schema(description = "阈值比较符（conditionConfig.operator，布尔型规则为空）", example = ">=")
    private String operator;

    @Schema(description = "恢复时间（yyyy-MM-dd HH:mm:ss，未恢复为空），用于确定趋势窗口终点")
    private String recoveryTime;

    @Schema(description = "命中的告警类型画像（数据库配置）：关联指标/可能原因/排查路径/建议动作；画像库为空时为 null")
    private DrilldownProfileVo profile;
}
