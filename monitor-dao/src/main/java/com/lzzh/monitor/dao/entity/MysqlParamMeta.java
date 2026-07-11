package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** MySQL 配置参数元数据（mysql_param_meta）。 */
@Data
@TableName("mysql_param_meta")
public class MysqlParamMeta {

    /** 参数名（小写，PRIMARY KEY）。 */
    @TableId
    private String paramName;

    /** 友好显示名（中文）。 */
    private String displayName;

    /** 分类：connection / innodb / logging / security / general。 */
    private String category;

    /** 是否动态可改（无需重启）。 */
    private Boolean isDynamic;

    /** 值单位（bytes / seconds / count / bool / 空字符串）。 */
    private String unit;

    /** 参数说明。 */
    private String description;

    /** 适用最低版本（5.6 / 5.7 / 8.0）。 */
    private String minVersion;

    /** 废弃版本（null 表示当前版本仍支持）。 */
    private String maxVersion;
}
