package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 数据库类型选项（新增/编辑实例的类型下拉，含默认端口与版本列表）。 */
@Data
@Schema(description = "数据库类型选项（含默认端口与版本列表）")
public class DbTypeOptionVo {

    @Schema(description = "类型ID（database_type.id，实例 dbTypeId 存储值）", example = "1")
    private Long id;

    @Schema(description = "类型编码", example = "MYSQL")
    private String code;

    @Schema(description = "展示名（同时作为实例 dbType 存储值）", example = "MySQL")
    private String label;

    @Schema(description = "默认端口", example = "3306")
    private Integer defaultPort;

    @Schema(description = "该类型支持的版本列表")
    private List<DbVersionOptionVo> versions;
}
