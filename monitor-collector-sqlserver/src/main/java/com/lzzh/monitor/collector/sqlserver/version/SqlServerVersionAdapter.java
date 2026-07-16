package com.lzzh.monitor.collector.sqlserver.version;

import com.lzzh.monitor.collector.spi.version.VersionAdapter;

/** SQL Server 版本适配器：所有采集 SQL 必须从明确版本适配器取得。 */
public interface SqlServerVersionAdapter extends VersionAdapter {

    /** 实例身份、Edition、HA 开关和启动时间。 */
    String identitySql();

    /** 当前数据库 Query Store 状态。 */
    String queryStoreCapabilitySql();
}
