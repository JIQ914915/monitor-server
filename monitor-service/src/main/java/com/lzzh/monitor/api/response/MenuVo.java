package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 前端动态菜单节点（与 monitor-web 的 MenuNode 对应，§11.11.7）。 */
@Schema(description = "前端动态菜单节点（与 monitor-web 的 MenuNode 对应）")
public record MenuVo(
        @Schema(description = "路由路径", example = "/system/menu") String path,
        @Schema(description = "路由名称", example = "SystemMenu") String name,
        @Schema(description = "前端组件路径", example = "system/menu/index") String component,
        @Schema(description = "重定向路径", example = "/system/menu/list") String redirect,
        @Schema(description = "菜单元信息") Meta meta,
        @Schema(description = "子菜单节点列表") List<MenuVo> children) {

    /** hidden：是否在菜单隐藏；activeMenu：详情类路由的高亮归属菜单 path。 */
    @Schema(description = "菜单元信息")
    public record Meta(
            @Schema(description = "菜单标题", example = "菜单管理") String title,
            @Schema(description = "菜单图标", example = "menu") String icon,
            @Schema(description = "权限标识", example = "system_menu") String perm,
            @Schema(description = "是否在菜单隐藏", example = "false") Boolean hidden,
            @Schema(description = "详情类路由的高亮归属菜单 path", example = "/system/menu") String activeMenu) {
    }

    /**
     * 构建目录（分组）节点。
     *
     * @param path     路由路径
     * @param name     路由名称
     * @param title    菜单标题
     * @param icon     菜单图标
     * @param children 子菜单节点列表
     * @return 目录类型菜单节点
     */
    public static MenuVo group(String path, String name, String title, String icon, List<MenuVo> children) {
        return new MenuVo(path, name, null, null, new Meta(title, icon, null, null, null), children);
    }

    /**
     * 构建叶子（可访问）菜单节点。
     *
     * @param path      路由路径
     * @param name      路由名称
     * @param component 前端组件路径
     * @param title     菜单标题
     * @param perm      权限标识
     * @return 叶子类型菜单节点
     */
    public static MenuVo leaf(String path, String name, String component, String title, String perm) {
        return new MenuVo(path, name, component, null, new Meta(title, null, perm, null, null), null);
    }

    /**
     * 构建隐藏路由节点：详情/编辑等不在菜单显示，高亮归属 activeMenu 指向的菜单。
     *
     * @param path       路由路径
     * @param name       路由名称
     * @param component  前端组件路径
     * @param title      菜单标题
     * @param activeMenu 高亮归属的菜单 path
     * @return 隐藏类型菜单节点
     */
    public static MenuVo hidden(String path, String name, String component, String title, String activeMenu) {
        return new MenuVo(path, name, component, null, new Meta(title, null, null, true, activeMenu), null);
    }
}
