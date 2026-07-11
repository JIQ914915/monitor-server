package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 配置参数分页查询请求（合并当前值 + 元数据，支持关键词 / 分类过滤）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "配置参数分页查询请求")
public class ParamPageRequest extends PageParam {

    @NotNull(message = "instanceId 不能为空")
    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long instanceId;

    @Schema(description = "关键词（模糊匹配参数名或说明），不传则不限")
    private String keyword;

    @Schema(description = "分类过滤（connection / innodb / logging / security / general），不传则不限")
    private String category;
}
