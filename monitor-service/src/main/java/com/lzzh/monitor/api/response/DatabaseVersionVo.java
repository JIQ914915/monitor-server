package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/** 数据库版本管理视图。 */
@Data
@Schema(description = "数据库版本管理视图")
public class DatabaseVersionVo {
    private Long id;
    private Long dbTypeId;
    private String dbType;
    private String versionCode;
    private String versionName;
    private Integer sortOrder;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
