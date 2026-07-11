package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通用状态切换入参，统一 POST body 传参。
 * <ul>
 *   <li>实例（/instances/toggle）：normal（恢复采集）/ paused（暂停采集）</li>
 *   <li>菜单/角色/字典等：enabled / disabled</li>
 * </ul>
 */
@Data
@Schema(description = "状态切换入参")
public class StatusRequest {

    @Schema(description = "主键 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "目标状态（各模块取值不同，实例：normal/paused；菜单/角色：enabled/disabled）",
            example = "normal", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "status 不能为空")
    private String status;
}
