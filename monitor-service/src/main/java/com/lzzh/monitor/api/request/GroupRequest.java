package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 实例分组新增/编辑（请求）。 */
@Data
@Schema(description = "实例分组新增/编辑请求")
public class GroupRequest {

    @Schema(description = "主键 ID，新增时为空、编辑时必填", example = "1")
    private Long id;

    @Schema(description = "分组名称", example = "生产环境")
    private String name;

    @Schema(description = "父分组 ID，顶级分组为空", example = "0")
    private Long parentId;

    @Schema(description = "分组负责人用户 ID", example = "10")
    private Long ownerId;

    @Schema(description = "分组成员用户 ID 列表", example = "[10,11,12]")
    private List<Long> memberIds;

    @Schema(description = "分组描述", example = "所有生产环境数据库实例")
    private String description;
}
