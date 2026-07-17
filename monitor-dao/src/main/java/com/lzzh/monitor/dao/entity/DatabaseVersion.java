package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/** 数据库版本配置（§21.2.1）。 */
@Data
@TableName(value = "database_version", autoResultMap = true)
public class DatabaseVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dbTypeId;

    /** 版本编码（5.6/5.7/8.0）。 */
    private String versionCode;

    private String versionName;

    /** 支持的功能特性，jsonb。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> supportedFeatures;

    private Integer sortOrder;

    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updatedAt;
}
