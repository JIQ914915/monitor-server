package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 用户选项（下拉/负责人选择用的轻量视图，不含敏感字段）。 */
@Data
@Schema(description = "用户选项（下拉/负责人选择用轻量视图）")
public class UserOptionVo {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "展示名（昵称优先，无则用户名）", example = "张三")
    private String name;

    public UserOptionVo() {
    }

    public UserOptionVo(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
