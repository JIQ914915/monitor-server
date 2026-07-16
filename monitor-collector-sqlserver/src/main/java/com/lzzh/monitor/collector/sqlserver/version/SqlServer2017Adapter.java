package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2017 基线适配器。 */
public class SqlServer2017Adapter implements SqlServerVersionAdapter {
    @Override
    public String version() {
        return "2017";
    }

    @Override
    public String identitySql() {
        return """
                SELECT CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS product_version,
                       CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition,
                       CAST(SERVERPROPERTY('EngineEdition') AS int) AS engine_edition,
                       CAST(SERVERPROPERTY('IsClustered') AS int) AS is_clustered,
                       CAST(SERVERPROPERTY('IsHadrEnabled') AS int) AS is_hadr_enabled,
                       DATEDIFF_BIG(second, sqlserver_start_time, SYSDATETIME()) AS uptime_seconds
                  FROM sys.dm_os_sys_info
                """;
    }

    @Override
    public String queryStoreCapabilitySql() {
        return """
                SELECT actual_state, desired_state, readonly_reason,
                       current_storage_size_mb, max_storage_size_mb,
                       query_capture_mode
                  FROM sys.database_query_store_options
                """;
    }
}
