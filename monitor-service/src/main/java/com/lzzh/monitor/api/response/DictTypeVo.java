package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/** 字典类型（响应）。 */
@Data
@Schema(description = "字典类型信息（响应）")
public class DictTypeVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "字典类型编码", example = "db_type")
    private String dictType;

    @Schema(description = "字典类型名称", example = "数据库类型")
    private String dictName;

    @Schema(description = "状态：enabled 启用 / disabled 停用", example = "enabled")
    private String status;

    @Schema(description = "字典范围：system 系统级 / custom 自定义", example = "system")
    private String type;

    @Schema(description = "备注", example = "系统内置字典类型")
    private String remark;

    @Schema(description = "创建时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
