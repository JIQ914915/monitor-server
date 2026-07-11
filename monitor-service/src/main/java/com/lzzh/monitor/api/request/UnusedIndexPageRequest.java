package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 疑似未使用索引分页查询入参（天级扫描结果）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "疑似未使用索引分页查询入参")
public class UnusedIndexPageRequest extends PageParam {

    @NotNull(message = "instanceId 不能为空")
    @Schema(description = "实例 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long instanceId;
}
