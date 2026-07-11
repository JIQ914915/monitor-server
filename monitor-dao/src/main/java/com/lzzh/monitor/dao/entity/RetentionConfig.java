package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数据保留策略（系统级，§12.2）。
 * 六类：minute/hourly/daily/slow_sql_sample/event/log/report，仅管理员可写；缩短保留期需二次确认。
 */
@Data
@TableName("retention_config")
public class RetentionConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类别：minute/hourly/daily/slow_sql_sample/event/log/report。 */
    private String category;

    /** 保留天数。 */
    private Integer retentionDays;

    /** 是否启用。 */
    private Boolean enabled;
}
