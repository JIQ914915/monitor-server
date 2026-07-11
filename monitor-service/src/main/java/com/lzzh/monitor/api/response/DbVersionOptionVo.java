package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 数据库版本选项（新增/编辑实例的版本下拉）。 */
@Data
@Schema(description = "数据库版本选项")
public class DbVersionOptionVo {

    @Schema(description = "版本ID（database_version.id，实例 dbVersionId 存储值）", example = "3")
    private Long id;

    @Schema(description = "版本编码", example = "8.0")
    private String value;

    @Schema(description = "版本展示名", example = "MySQL 8.0")
    private String label;


    public DbVersionOptionVo() {
    }

    public DbVersionOptionVo(Long id, String value, String label) {
        this.id = id;
        this.value = value;
        this.label = label;
    }
}
