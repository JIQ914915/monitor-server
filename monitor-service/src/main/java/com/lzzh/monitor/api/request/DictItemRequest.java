package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 字典项新增/编辑请求。 */
@Data
@Schema(description = "字典项新增/编辑请求")
public class DictItemRequest {

    @Schema(description = "主键 ID，新增时为空，编辑时必填", example = "1")
    private Long id;

    @Schema(description = "所属字典类型编码", example = "db_type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "所属字典类型不能为空")
    private String dictType;

    @Schema(description = "字典值，同一字典类型下唯一", example = "mysql", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典值不能为空")
    private String itemValue;

    @Schema(description = "字典标签（展示文本）", example = "MySQL", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "字典标签不能为空")
    private String itemLabel;

    /** 标签颜色：success/warning/danger/info/primary。 */
    @Schema(description = "标签颜色：success/warning/danger/info/primary", example = "success")
    private String tagType;

    @Schema(description = "排序值，升序展示，不传默认 0", example = "1")
    private Integer sort;

    @Schema(description = "状态：enabled 启用 / disabled 停用，新增不传默认 enabled", example = "enabled")
    private String status;

    @Schema(description = "备注", example = "MySQL 数据库")
    private String remark;
}
