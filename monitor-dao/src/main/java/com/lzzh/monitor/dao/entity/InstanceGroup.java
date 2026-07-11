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

/** 实例分组（父子层级 + 负责人/成员，数据范围授权依据）。 */
@Data
@TableName(value = "instance_group", autoResultMap = true)
public class InstanceGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long parentId;

    /** 负责人用户ID（关联 sys_user.id）。 */
    private Long ownerId;

    /** 小组成员用户ID集合，jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> memberIds;

    private String description;

    @TableField("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;
    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
