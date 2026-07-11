package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 按字典类型查询字典项。 */
@Data
@Schema(description = "按字典类型查询字典项的请求")
public class DictItemQuery {

    @Schema(description = "字典类型编码", example = "db_type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典类型不能为空")
    private String dictType;
}
