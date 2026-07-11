package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 角色（§11.11.6：菜单与按钮权限码 menu:action 集合）。 */
@Data
@TableName(value = "sys_role", autoResultMap = true)
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private String name;

    /** 角色类型：字典 role_type 值（preset 预设 / custom 自定义；预设角色不可删除）。 */
    private String type;

    /** 状态：enabled / disabled。 */
    private String status;

    /** 角色描述。 */
    private String description;

    /** 权限码集合，如 instance:add、data_retention:write，jsonb 存储；鉴权唯一依据。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> permissions;

    /** 数据范围：all/group/self。 */
    private String dataScope;

    /** 数据范围为 group 时的分组ID集合，jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> dataScopeGroups;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("created_at")
    private OffsetDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField("updated_at")
    private OffsetDateTime updateTime;
}
