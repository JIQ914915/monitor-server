package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2022 适配器；权限契约使用 VIEW SERVER/DATABASE PERFORMANCE STATE。 */
public class SqlServer2022Adapter extends SqlServer2019Adapter {
    @Override
    public String version() {
        return "2022";
    }
}
