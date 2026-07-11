package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 菜单内的按钮权限点（响应）。 */
@Data
@Schema(description = "菜单内的按钮权限点（响应）")
public class MenuButtonVo {

    @Schema(description = "按钮名称", example = "新增")
    private String name;

    @Schema(description = "按钮权限编码", example = "menu:add")
    private String code;

    @Schema(description = "状态：enabled 启用 / disabled 停用", example = "enabled")
    private String status;
}
