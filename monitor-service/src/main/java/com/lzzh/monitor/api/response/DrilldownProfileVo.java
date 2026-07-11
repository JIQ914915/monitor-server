package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警下钻画像（§11.7 事件下钻）。
 * <p>四块内容均为 JSON 结构直传前端渲染：
 * relatedMetrics / causes / steps / actions，字段约定见表注释与管理页说明。
 */
@Data
@Schema(description = "告警下钻画像：匹配规则 + 关联指标/可能原因/排查路径/建议动作")
public class DrilldownProfileVo {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "画像编码（唯一）", example = "connections")
    private String profileCode;

    @Schema(description = "画像名称", example = "连接与会话类")
    private String profileLabel;

    @Schema(description = "适用数据库类型", example = "mysql")
    private String dbType;

    @Schema(description = "匹配规则：[{matchType:exact|prefix, pattern}]，空数组=仅作兜底")
    private List<Map<String, Object>> matchRules;

    @Schema(description = "关联指标：[{code,label,unit,color,toGB}]")
    private List<Map<String, Object>> relatedMetrics;

    @Schema(description = "可能原因：[{cause,confidence,color,evidence[]}]")
    private List<Map<String, Object>> causes;

    @Schema(description = "排查路径：[{title,description,action,link}]，link 为页面编码")
    private List<Map<String, Object>> steps;

    @Schema(description = "建议动作：[{action,risk,description,sql,impact}]")
    private List<Map<String, Object>> actions;

    @Schema(description = "是否内置（内置不可删除）")
    private Boolean builtin;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "更新时间")
    private String updatedAt;
}
