package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2012 兼容适配器：无 Query Store，Top SQL 使用 DMV 累计快照。 */
public class SqlServer2012Adapter extends SqlServer2017Adapter {
    @Override public String version() { return "2012"; }
    @Override public boolean supportsQueryStore() { return false; }
    @Override public boolean supportsVlfDmv() { return false; }

    @Override
    public String identitySql() {
        return """
                SELECT CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS product_version,
                       CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition,
                       CAST(SERVERPROPERTY('EngineEdition') AS int) AS engine_edition,
                       CAST(SERVERPROPERTY('IsClustered') AS int) AS is_clustered,
                       CAST(SERVERPROPERTY('IsHadrEnabled') AS int) AS is_hadr_enabled,
                       DATEDIFF(second, sqlserver_start_time, SYSDATETIME()) AS uptime_seconds
                  FROM sys.dm_os_sys_info
                """;
    }

    @Override
    public String queryStoreCapabilitySql() {
        return """
                SELECT CAST(NULL AS int) AS actual_state,
                       CAST(NULL AS int) AS desired_state,
                       CAST(NULL AS bigint) AS readonly_reason,
                       CAST(NULL AS bigint) AS current_storage_size_mb,
                       CAST(NULL AS bigint) AS max_storage_size_mb,
                       CAST(NULL AS int) AS query_capture_mode
                 WHERE 1=0
                """;
    }

    @Override
    public String queryStoreTopSql() {
        return """
                SELECT CAST(NULL AS nvarchar(128)) AS database_name,
                       CAST(NULL AS varchar(64)) AS digest,
                       CAST(NULL AS nvarchar(2000)) AS sql_text,
                       CAST(NULL AS bigint) AS executions,
                       CAST(NULL AS bigint) AS duration_us,
                       CAST(NULL AS bigint) AS logical_reads,
                       CAST(NULL AS bigint) AS physical_reads,
                       CAST(NULL AS bigint) AS writes,
                       CAST(NULL AS bigint) AS rows_count
                 WHERE 1=0
                """;
    }

    @Override
    public String vlfSql() {
        return "DBCC LOGINFO WITH NO_INFOMSGS";
    }
    @Override
    public String queryStoreRegressionSql() {
        return """
                SELECT CAST(NULL AS varchar(64)) AS digest,CAST(NULL AS bigint) AS current_executions,
                       CAST(NULL AS int) AS current_plan_count,CAST(NULL AS int) AS has_new_plan,
                       CAST(NULL AS float) AS regression_ratio WHERE 1=0
                """;
    }}
