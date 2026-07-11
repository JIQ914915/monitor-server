package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/** 数据库版本管理 VO（供系统设置页维护）。 */
@Data
@Schema(description = "数据库版本管理视图")
public class DatabaseVersionVo {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "数据库类型（如 mysql）")
    private String dbType;

    @Schema(description = "版本编码，如 8.0")
    private String versionCode;

    @Schema(description = "版本名称，如 MySQL 8.0")
    private String versionName;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}
