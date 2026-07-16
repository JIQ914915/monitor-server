package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2019 适配器。 */
public class SqlServer2019Adapter extends SqlServer2017Adapter {
    @Override
    public String version() {
        return "2019";
    }
}
