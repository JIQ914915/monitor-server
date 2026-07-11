package com.lzzh.monitor.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 当前用户信息（与前端 UserInfo 对应）。 */
@Schema(description = "当前登录用户信息（与前端 UserInfo 对应）")
public record UserInfoVo(
        @Schema(description = "主键 ID", example = "1") Long id,
        @Schema(description = "用户名（登录账号）", example = "admin") String username,
        @Schema(description = "昵称", example = "管理员") String nickname,
        @Schema(description = "角色编码列表", example = "[\"admin\"]") List<String> roles,
        @Schema(description = "权限编码列表", example = "[\"system_user\"]") List<String> permissions) {
}
