package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 表 I/O 热点分页查询入参（近 1 小时，按等待耗时降序）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "表 I/O 热点分页查询入参")
public class TableIoPageRequest extends PageParam {

    @NotNull(message = "instanceId 不能为空")
    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long instanceId;
}
