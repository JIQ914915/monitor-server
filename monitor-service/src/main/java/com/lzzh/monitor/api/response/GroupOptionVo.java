package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 分组选项（下拉/数据范围选择用的轻量视图，仅 id + name）。 */
@Data
@Schema(description = "分组选项（下拉/数据范围选择用轻量视图）")
public class GroupOptionVo {

    @Schema(description = "分组ID", example = "1")
    private Long id;

    @Schema(description = "分组名称", example = "订单系统组")
    private String name;

    public GroupOptionVo() {
    }

    public GroupOptionVo(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
