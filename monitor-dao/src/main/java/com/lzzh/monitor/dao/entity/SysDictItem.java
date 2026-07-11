package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;

/** 数据字典项（值/标签/标签颜色/排序）。 */
@Data
@TableName("sys_dict_item")
public class SysDictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属字典类型编码。 */
    private String dictType;

    /** 字典值，如 normal。 */
    private String itemValue;

    /** 展示标签，如 正常。 */
    private String itemLabel;

    /** 标签颜色：success/warning/danger/info/primary。 */
    private String tagType;

    private Integer sort;

    /** 状态：enabled/disabled。 */
    private String status;

    private String remark;

    @TableField("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createTime;
    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
