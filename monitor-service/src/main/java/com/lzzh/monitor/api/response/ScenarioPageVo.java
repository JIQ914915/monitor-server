package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 场景列表响应（含统计卡片数据，按启停页签 + 级别排序后端分页）。 */
@Data
@Schema(description = "场景列表响应")
public class ScenarioPageVo {

    @Schema(description = "适配当前实例的场景总数（不含页签过滤）")
    private Integer total;

    @Schema(description = "当前页签过滤后的总数（分页组件用）")
    private Integer filteredTotal;

    @Schema(description = "已启用数")
    private Integer enabledCount;

    @Schema(description = "已停用数")
    private Integer disabledCount;

    @Schema(description = "当前页场景列表")
    private List<ScenarioVo> scenarios;
}
