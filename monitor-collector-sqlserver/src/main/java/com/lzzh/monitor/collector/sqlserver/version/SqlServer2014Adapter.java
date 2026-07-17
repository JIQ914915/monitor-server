package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2014 兼容适配器：沿用 2012 DMV 契约，不支持 Query Store。 */
public class SqlServer2014Adapter extends SqlServer2012Adapter {
    @Override public String version() { return "2014"; }
}
