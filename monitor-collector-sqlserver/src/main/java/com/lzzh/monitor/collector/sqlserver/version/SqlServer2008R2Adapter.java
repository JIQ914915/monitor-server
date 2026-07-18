package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2008 R2 兼容适配器：无 Query Store/Always On，并适配旧版日志与 Top SQL DMV。 */
public class SqlServer2008R2Adapter extends SqlServer2012Adapter {
    @Override public String version() { return "2008"; }
    @Override public boolean supportsAlwaysOn() { return false; }

    @Override
    public String identitySql() {
        return """
                SELECT CAST(SERVERPROPERTY('ProductVersion') AS nvarchar(128)) AS product_version,
                       CAST(SERVERPROPERTY('Edition') AS nvarchar(128)) AS edition,
                       CAST(SERVERPROPERTY('EngineEdition') AS int) AS engine_edition,
                       CAST(SERVERPROPERTY('IsClustered') AS int) AS is_clustered,
                       CAST(0 AS int) AS is_hadr_enabled,
                       DATEDIFF(second, sqlserver_start_time, SYSDATETIME()) AS uptime_seconds
                  FROM sys.dm_os_sys_info
                """;
    }

    @Override
    public String storageSql() {
        return """
                SELECT
                  SUM(CASE WHEN f.type=0 THEN f.size*8192.0 ELSE 0 END) AS data_size_bytes,
                  SUM(CASE WHEN f.type=0 THEN FILEPROPERTY(f.name,'SpaceUsed')*8192.0 ELSE 0 END) AS data_used_bytes,
                  SUM(CASE WHEN f.type=1 THEN f.size*8192.0 ELSE 0 END) AS log_size_bytes,
                  MAX(pc.log_used_percent) AS log_used_percent,
                  SUM(v.num_of_reads) AS io_reads,
                  SUM(v.num_of_writes) AS io_writes,
                  SUM(v.io_stall_read_ms) AS io_stall_read_ms,
                  SUM(v.io_stall_write_ms) AS io_stall_write_ms,
                  MAX(d.log_reuse_wait_desc) AS log_reuse_wait_desc
                FROM sys.database_files f
                LEFT JOIN sys.dm_io_virtual_file_stats(DB_ID(),NULL) v ON v.file_id=f.file_id
                CROSS JOIN (
                  SELECT MAX(CASE WHEN counter_name='Log File(s) Used Size (KB)' THEN cntr_value END)*100.0/
                         NULLIF(MAX(CASE WHEN counter_name='Log File(s) Size (KB)' THEN cntr_value END),0)
                         AS log_used_percent
                    FROM sys.dm_os_performance_counters
                   WHERE instance_name=DB_NAME()
                ) pc
                CROSS JOIN sys.databases d
                WHERE d.database_id=DB_ID()
                """;
    }

    @Override
    public String dmvTopSql() {
        return """
                SELECT TOP (50) DB_NAME(st.dbid) AS database_name,
                       CONVERT(varchar(64), qs.query_hash, 2) AS digest,
                       LEFT(SUBSTRING(st.text,(qs.statement_start_offset/2)+1,
                         ((CASE qs.statement_end_offset WHEN -1 THEN DATALENGTH(st.text)
                           ELSE qs.statement_end_offset END-qs.statement_start_offset)/2)+1),2000) AS sql_text,
                       qs.execution_count AS executions, qs.total_elapsed_time AS duration_us,
                       qs.total_logical_reads AS logical_reads, qs.total_physical_reads AS physical_reads,
                       qs.total_logical_writes AS writes, CAST(0 AS bigint) AS rows_count
                  FROM sys.dm_exec_query_stats qs
                  CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
                 ORDER BY qs.total_elapsed_time DESC
                """;
    }
}
