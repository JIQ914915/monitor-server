package com.lzzh.monitor.dao.entity;

import lombok.Data;

/** 菜单内的按钮权限点（权限标识用于后端鉴权与角色授权）。 */
@Data
public class MenuButton {

    /** 按钮名称，如 新增/编辑/导出。 */
    private String name;

    /** 权限标识，全局唯一，建议 菜单编码:操作，如 user:create。 */
    private String code;

    /** 状态：enabled/disabled。 */
    private String status;
}
