package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;

/** 数据库类型管理 VO（完整字段，供系统设置页维护）。 */
@Data
@Schema(description = "数据库类型管理视图（完整字段）")
public class DatabaseTypeVo {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "类型编码，如 MYSQL")
    private String code;

    @Schema(description = "展示名，如 MySQL")
    private String label;

    @Schema(description = "JDBC 驱动类名")
    private String driverClass;

    @Schema(description = "JDBC URL 模板")
    private String urlTemplate;

    @Schema(description = "采集器实现类全限定名")
    private String collectorClass;

    @Schema(description = "默认端口")
    private Integer defaultPort;

    @Schema(description = "图标URL")
    private String dbIcon;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}
