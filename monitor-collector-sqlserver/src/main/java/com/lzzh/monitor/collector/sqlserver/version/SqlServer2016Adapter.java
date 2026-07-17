package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2016 兼容适配器：Query Store 可用但状态由目标数据库配置决定。 */
public class SqlServer2016Adapter extends SqlServer2017Adapter {
    @Override public String version() { return "2016"; }
}
