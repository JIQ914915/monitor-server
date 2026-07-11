package com.lzzh.monitor.api.request;

import com.lzzh.monitor.common.result.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户分页查询。 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询条件")
public class UserPageRequest extends PageParam {

    /** 角色编码过滤。 */
    @Schema(description = "角色编码过滤", example = "admin")
    private String roleCode;

    /** 启停状态过滤（null 表示全部）。 */
    @Schema(description = "启停状态过滤（null 表示全部）", example = "true")
    private Boolean enabled;
}
