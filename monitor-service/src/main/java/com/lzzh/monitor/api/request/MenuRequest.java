package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 菜单新增/编辑（请求，含按钮权限点）。 */
@Data
@Schema(description = "菜单新增/编辑请求（含按钮权限点）")
public class MenuRequest {

    @Schema(description = "主键 ID，新增时为空、编辑时必填", example = "1")
    private Long id;

    @Schema(description = "菜单名称", example = "菜单管理")
    private String name;

    @Schema(description = "菜单编码（唯一）", example = "system_menu")
    private String code;

    @Schema(description = "类型：目录 / 菜单 / 按钮", example = "menu")
    private String type;

    @Schema(description = "菜单类型（前端路由分类）", example = "route")
    private String menuType;

    @Schema(description = "父菜单 ID，顶级菜单为 0 或空", example = "0")
    private Long parentId;

    @Schema(description = "菜单图标", example = "menu")
    private String icon;

    @Schema(description = "前端路由路径", example = "/system/menu")
    private String route;

    @Schema(description = "前端组件路径", example = "system/menu/index")
    private String component;

    @Schema(description = "重定向路径", example = "/system/menu/list")
    private String redirect;

    @Schema(description = "权限标识", example = "system_menu")
    private String perm;

    @Schema(description = "排序号，越小越靠前", example = "1")
    private Integer sort;

    @Schema(description = "是否在菜单中可见", example = "true")
    private Boolean visible;

    @Schema(description = "详情类路由高亮归属的菜单 path", example = "/system/menu")
    private String activeMenu;

    @Schema(description = "状态：enabled 启用 / disabled 停用", example = "enabled")
    private String status;

    @Schema(description = "菜单描述", example = "系统菜单维护")
    private String description;

    @Schema(description = "菜单内的按钮权限点列表")
    private List<MenuButtonDto> buttons;
}
