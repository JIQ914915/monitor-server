package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 菜单（菜单管理维护；权限码 menu:action）。 */
@Data
@TableName(value = "sys_menu", autoResultMap = true)
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String code;

    /** 系统级/实例级（业务分类）。 */
    private String type;

    /** 节点类型：group 目录/分组、menu 页面。 */
    private String menuType;

    /** 上级菜单ID（NULL=顶级），层级由此表达。 */
    private Long parentId;

    private String icon;

    /** 前端路由 path 段（相对父节点）。 */
    private String route;

    /** 组件路径（相对 src/views，不含 .vue），目录为空。 */
    private String component;

    /** 重定向目标 path（可空）。 */
    private String redirect;

    /** 访问权限码 menu:action。 */
    private String perm;

    private Integer sort;

    /** 是否在侧边菜单显示（隐藏页面置 false）。 */
    private Boolean visible;

    /** 隐藏页面的高亮归属菜单 path。 */
    private String activeMenu;

    /** enabled/disabled。 */
    private String status;

    private String description;

    /** 按钮权限点集合，jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<MenuButton> buttons;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("created_at")
    private OffsetDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("updated_at")
    private OffsetDateTime updateTime;
}
