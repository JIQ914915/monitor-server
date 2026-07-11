package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 告警下钻画像保存入参（id 为空新增，否则更新）。 */
@Data
@Schema(description = "告警下钻画像保存入参")
public class DrilldownProfileSaveRequest {

    @Schema(description = "主键 ID，为空表示新增")
    private Long id;

    @Schema(description = "画像编码（唯一）", example = "connections", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "画像编码不能为空")
    private String profileCode;

    @Schema(description = "画像名称", example = "连接与会话类", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "画像名称不能为空")
    private String profileLabel;

    @Schema(description = "适用数据库类型", example = "mysql")
    private String dbType;

    @Schema(description = "匹配规则：[{matchType:exact|prefix, pattern}]")
    private List<Map<String, Object>> matchRules;

    @Schema(description = "关联指标：[{code,label,unit,color,toGB}]")
    private List<Map<String, Object>> relatedMetrics;

    @Schema(description = "可能原因：[{cause,confidence,color,evidence[]}]")
    private List<Map<String, Object>> causes;

    @Schema(description = "排查路径：[{title,description,action,link}]")
    private List<Map<String, Object>> steps;

    @Schema(description = "建议动作：[{action,risk,description,sql,impact}]")
    private List<Map<String, Object>> actions;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "排序号", example = "1")
    private Integer sort;

    @Schema(description = "备注")
    private String remark;
}
