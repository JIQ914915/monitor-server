package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 实例分组（响应）。 */
@Data
@Schema(description = "实例分组信息（响应）")
public class GroupVo {

    @Schema(description = "主键 ID", example = "1")
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

    @Schema(description = "分组下的实例数", example = "8")
    private Long instanceCount;

    @Schema(description = "创建时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
