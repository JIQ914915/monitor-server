package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 字典类型新增/编辑请求。 */
@Data
@Schema(description = "字典类型新增/编辑请求")
public class DictTypeRequest {

    @Schema(description = "主键 ID，新增时为空，编辑时必填", example = "1")
    private Long id;

    @Schema(description = "字典类型编码，全局唯一", example = "db_type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典类型编码不能为空")
    private String dictType;

    @Schema(description = "字典类型名称", example = "数据库类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典类型名称不能为空")
    private String dictName;

    @Schema(description = "状态：enabled 启用 / disabled 停用，新增不传默认 enabled", example = "enabled")
    private String status;

    @Schema(description = "字典范围：system 系统级 / custom 自定义；非超管新增固定为 custom", example = "custom")
    private String type;

    @Schema(description = "备注", example = "系统内置字典类型")
    private String remark;
}
