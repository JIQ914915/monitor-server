package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 场景列表查询请求（实例级页面，按启停页签 + 级别排序后端分页）。 */
@Data
@Schema(description = "场景列表查询请求")
public class ScenarioPageRequest {

    @NotNull(message = "实例 ID 不能为空")
    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long instanceId;

    @Schema(description = "启停页签过滤：true=已启用 / false=已停用 / null=全部")
    private Boolean enabled;

    @Schema(description = "页码（默认 1）")
    private Integer pageNum;

    @Schema(description = "每页条数（默认 10）")
    private Integer pageSize;
}
