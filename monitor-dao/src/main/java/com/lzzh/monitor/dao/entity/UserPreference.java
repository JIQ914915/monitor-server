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

/** 用户个性化偏好（账号级主题持久化，方案 §8.5）。 */
@Data
@TableName(value = "sys_user_preference", autoResultMap = true)
public class UserPreference {

    /** 主键 = 用户ID（非自增，由业务写入）。 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 主题配置，jsonb 存储。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> theme;

    @TableField("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private OffsetDateTime updateTime;
}
