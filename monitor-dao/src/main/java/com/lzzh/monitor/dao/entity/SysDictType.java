package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

/** 数据字典类型（如 instance_status 实例状态）。 */
@Data
@TableName("sys_dict_type")
public class SysDictType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型编码，如 instance_status。 */
    private String dictType;

    /** 字典类型名称，如 实例状态。 */
    private String dictName;

    /** 状态：enabled/disabled。 */
    private String status;

    /** 字典范围：system 系统级 / custom 自定义。 */
    private String type;

    private String remark;

    @TableField("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;
    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
