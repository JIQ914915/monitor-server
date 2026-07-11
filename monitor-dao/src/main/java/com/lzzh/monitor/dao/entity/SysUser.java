package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 系统用户（§5.5：多角色 roles，权限取并集）。 */
@Data
@TableName(value = "sys_user", autoResultMap = true)
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String nickname;

    /** 邮箱。 */
    private String email;

    /** 联系电话。 */
    private String phone;

    /** BCrypt 加密后的密码。 */
    private String password;

    /** 角色编码集合（多角色），jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> roles;

    private Boolean enabled;

    /** 最后登录时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginTime;

    @TableField("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;
    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
