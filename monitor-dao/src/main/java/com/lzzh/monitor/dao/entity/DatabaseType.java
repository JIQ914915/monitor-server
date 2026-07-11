package com.lzzh.monitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 数据库类型登记表（§5.8 扩展性核心）。
 * 新增数据库类型时在此登记 collectorClass 等连接元数据；具体支持版本由 database_version 表维护。
 */
@Data
@TableName("database_type")
public class DatabaseType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型编码，对应 DbType，如 MYSQL。 */
    private String code;

    /** 展示名，如 MySQL。 */
    private String label;

    /** 采集器实现类全限定名（由 SPI 工厂解析时校验/登记）。 */
    private String collectorClass;

    /** JDBC 驱动类名，如 com.mysql.cj.jdbc.Driver。 */
    private String driverClass;

    /** JDBC URL 模板，如 jdbc:mysql://{host}:{port}/{database}。 */
    private String urlTemplate;

    /** 默认端口。 */
    private Integer defaultPort;

    /** 图标 URL。 */
    private String dbIcon;

    /** 排序序号。 */
    private Integer sortOrder;

    /** 说明。 */
    private String description;

    private Boolean enabled;
}
