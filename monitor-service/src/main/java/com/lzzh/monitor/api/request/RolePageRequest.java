package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "角色分页查询请求（含分页与关键字，继承自 PageParam）")
public class RolePageRequest extends PageParam {

    /** 角色类型：字典 role_type 值（preset/custom）。 */
    @Schema(description = "角色类型：字典 role_type 值（preset 预设 / custom 自定义）", example = "custom")
    private String type;

    /** 状态：enabled/disabled。 */
    @Schema(description = "状态：enabled 启用 / disabled 停用", example = "enabled")
    private String status;
}
