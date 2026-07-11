package com.lzzh.monitor.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 用户新增/编辑（请求）。 */
@Data
@Schema(description = "用户新增/编辑请求体")
public class UserRequest {

    @Schema(description = "主键 ID（新增留空，编辑必填）", example = "1")
    private Long id;

    @Schema(description = "用户名（登录账号）", example = "zhangsan")
    private String username;

    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    /** 明文密码：新增留空用默认口令，编辑留空表示不修改；由控制器加密后回填。 */
    @Schema(description = "明文密码：新增留空用默认口令，编辑留空表示不修改", example = "123456")
    private String password;

    @Schema(description = "角色编码列表", example = "[\"admin\"]")
    private List<String> roles;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;
}
