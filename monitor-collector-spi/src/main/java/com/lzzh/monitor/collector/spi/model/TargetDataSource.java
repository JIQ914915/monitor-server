package com.lzzh.monitor.collector.spi.model;

import lombok.Data;

/** 被监控库连接信息（密码已解密，由 collector 的目标库连接池管理，§8.4）。 */
@Data
public class TargetDataSource {
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClass;
}
