package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2025 显式适配器，禁止以 2022 隐式回退宣称支持。 */
public class SqlServer2025Adapter extends SqlServer2022Adapter {
    @Override
    public String version() {
        return "2025";
    }
}
