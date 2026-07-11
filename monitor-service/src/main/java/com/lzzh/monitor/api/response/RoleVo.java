package com.lzzh.monitor.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 角色（响应）。 */
@Data
@Schema(description = "角色信息（响应）")
public class RoleVo {

    @Schema(description = "主键 ID", example = "1")
    private Long id;

    @Schema(description = "角色编码，全局唯一", example = "dba")
    private String code;

    @Schema(description = "角色名称", example = "数据库管理员")
    private String name;

    @Schema(description = "角色类型：字典 role_type 值（preset 预设 / custom 自定义）", example = "custom")
    private String type;

    @Schema(description = "状态：enabled 启用 / disabled 停用", example = "enabled")
    private String status;

    @Schema(description = "角色描述", example = "拥有实例与采集配置的管理权限")
    private String description;

    @Schema(description = "权限码集合（menu:action 形式）", example = "[\"instance:add\",\"instance:edit\"]")
    private List<String> permissions;

    @Schema(description = "数据范围：all 全部 / group 指定分组 / self 仅本人", example = "all")
    private String dataScope;

    @Schema(description = "数据范围为 group 时授权的分组 ID 列表", example = "[1,2]")
    private List<Long> dataScopeGroups;

    @Schema(description = "拥有该角色的用户数", example = "5")
    private Long userCount;

    @Schema(description = "创建时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-02 11:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
