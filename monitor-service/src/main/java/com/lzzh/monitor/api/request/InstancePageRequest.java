package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 实例分页查询（关键字 + 类型/状态/分组过滤）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实例分页查询入参")
public class InstancePageRequest extends PageParam {

    @Schema(description = "数据库类型ID过滤（database_type.id）", example = "1")
    private Long dbTypeId;

    @Schema(description = "状态过滤：normal / abnormal / paused", example = "normal")
    private String status;

    @Schema(description = "按所属分组过滤（groupIds 包含该分组 ID）", example = "5")
    private Long groupId;
}
