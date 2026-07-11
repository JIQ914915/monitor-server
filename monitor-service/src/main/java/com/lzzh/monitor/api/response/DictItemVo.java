package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/** 字典项（响应）。 */
@Data
@Schema(description = "字典项信息（响应）")
public class DictItemVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "所属字典类型编码", example = "db_type")
    private String dictType;

    @Schema(description = "字典值", example = "mysql")
    private String itemValue;

    @Schema(description = "字典标签（展示文本）", example = "MySQL")
    private String itemLabel;

    @Schema(description = "标签颜色：success/warning/danger/info/primary", example = "success")
    private String tagType;

    @Schema(description = "排序值，升序展示", example = "1")
    private Integer sort;

    @Schema(description = "状态：enabled 启用 / disabled 停用", example = "enabled")
    private String status;

    @Schema(description = "备注", example = "MySQL 数据库")
    private String remark;

    @Schema(description = "创建时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
