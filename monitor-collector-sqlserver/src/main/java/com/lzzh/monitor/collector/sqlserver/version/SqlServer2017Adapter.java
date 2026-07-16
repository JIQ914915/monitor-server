package com.lzzh.monitor.collector.sqlserver.version;

/** SQL Server 2017 基线适配器；2019/2022/2025 显式继承稳定 DMV 契约。 */
public class SqlServer2017Adapter implements SqlServerVersionAdapter {
    @Override public String version() { return "2017"; }

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
                       current_storage_size_mb, max_storage_size_mb, query_capture_mode
                  FROM sys.database_query_store_options
                """;
    }

    @Override
    public String performanceCountersSql() {
        return """
                SELECT
                  MAX(CASE WHEN counter_name='User Connections' THEN cntr_value END) AS user_connections,
                  MAX(CASE WHEN counter_name='Batch Requests/sec' THEN cntr_value END) AS batch_requests_total,
                  MAX(CASE WHEN counter_name='SQL Compilations/sec' THEN cntr_value END) AS compilations_total,
                  MAX(CASE WHEN counter_name='SQL Re-Compilations/sec' THEN cntr_value END) AS recompilations_total,
                  MAX(CASE WHEN counter_name='Memory Grants Pending' THEN cntr_value END) AS memory_grants_pending,
                  MAX(CASE WHEN counter_name='Memory Grants Outstanding' THEN cntr_value END) AS memory_grants_outstanding,
                  MAX(CASE WHEN counter_name='Total Server Memory (KB)' THEN cntr_value END) AS total_server_memory_kb,
                  MAX(CASE WHEN counter_name='Target Server Memory (KB)' THEN cntr_value END) AS target_server_memory_kb,
                  MAX(CASE WHEN counter_name='Page life expectancy' THEN cntr_value END) AS page_life_expectancy,
                  MAX(CASE WHEN counter_name='Lazy writes/sec' THEN cntr_value END) AS lazy_writes_total,
                  MAX(CASE WHEN counter_name='Page reads/sec' THEN cntr_value END) AS page_reads_total,
                  MAX(CASE WHEN counter_name='Page writes/sec' THEN cntr_value END) AS page_writes_total,
                  MAX(CASE WHEN counter_name='Number of Deadlocks/sec' THEN cntr_value END) AS deadlocks_total
                FROM sys.dm_os_performance_counters
                WHERE instance_name IN ('', '_Total')
                """;
    }

    @Override
    public String runtimeSql() {
        return """
                SELECT
                  (SELECT COALESCE(SUM(runnable_tasks_count),0) FROM sys.dm_os_schedulers
                    WHERE status='VISIBLE ONLINE') AS runnable_tasks,
                  (SELECT COALESCE(SUM(active_workers_count),0) FROM sys.dm_os_schedulers
                    WHERE status='VISIBLE ONLINE') AS active_workers,
                  (SELECT COALESCE(SUM(current_tasks_count),0) FROM sys.dm_os_schedulers
                    WHERE status='VISIBLE ONLINE') AS current_tasks,
                  (SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE is_user_process=1) AS user_sessions,
                  (SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id<>@@SPID) AS active_requests,
                  (SELECT COUNT(*) FROM sys.dm_exec_requests
                    WHERE blocking_session_id>0 AND session_id<>@@SPID) AS blocked_sessions,
                  (SELECT COALESCE(MAX(DATEDIFF(second,start_time,SYSDATETIME())),0)
                     FROM sys.dm_exec_requests WHERE session_id<>@@SPID) AS max_request_seconds,
                  (SELECT COALESCE(MAX(open_transaction_count),0)
                     FROM sys.dm_exec_sessions WHERE is_user_process=1) AS max_open_transactions
                """;
    }

    @Override
    public String waitStatsSql() {
        return """
                SELECT wait_category, SUM(wait_time_ms) AS wait_time_ms,
                       SUM(signal_wait_time_ms) AS signal_wait_time_ms,
                       SUM(waiting_tasks_count) AS waiting_tasks_count
                FROM (
                  SELECT CASE
                    WHEN wait_type LIKE 'LCK[_]%' THEN 'lock'
                    WHEN wait_type LIKE 'PAGEIOLATCH[_]%' OR wait_type LIKE 'IO_COMPLETION%' THEN 'io'
                    WHEN wait_type LIKE 'WRITELOG%' THEN 'log'
                    WHEN wait_type LIKE 'RESOURCE_SEMAPHORE%' THEN 'memory'
                    WHEN wait_type LIKE 'CXPACKET%' OR wait_type LIKE 'CXCONSUMER%' THEN 'parallel'
                    WHEN wait_type LIKE 'HADR[_]%' OR wait_type LIKE 'REDO[_]%' THEN 'ha'
                    WHEN wait_type LIKE 'ASYNC_NETWORK_IO%' THEN 'network'
                    WHEN wait_type LIKE 'SOS_SCHEDULER_YIELD%' THEN 'cpu'
                    ELSE 'other' END AS wait_category,
                    wait_time_ms, signal_wait_time_ms, waiting_tasks_count
                  FROM sys.dm_os_wait_stats
                  WHERE wait_type NOT IN ('SLEEP_TASK','SLEEP_SYSTEMTASK','LAZYWRITER_SLEEP',
                    'SQLTRACE_BUFFER_FLUSH','XE_TIMER_EVENT','XE_DISPATCHER_WAIT',
                    'REQUEST_FOR_DEADLOCK_SEARCH','BROKER_TO_FLUSH','BROKER_TASK_STOP')
                ) w
                GROUP BY wait_category
                """;
    }

    @Override
    public String storageSql() {
        return """
                SELECT
                  SUM(CASE WHEN f.type=0 THEN f.size*8192.0 ELSE 0 END) AS data_size_bytes,
                  SUM(CASE WHEN f.type=0 THEN FILEPROPERTY(f.name,'SpaceUsed')*8192.0 ELSE 0 END) AS data_used_bytes,
                  SUM(CASE WHEN f.type=1 THEN f.size*8192.0 ELSE 0 END) AS log_size_bytes,
                  MAX(ls.used_log_space_in_percent) AS log_used_percent,
                  SUM(v.num_of_reads) AS io_reads,
                  SUM(v.num_of_writes) AS io_writes,
                  SUM(v.io_stall_read_ms) AS io_stall_read_ms,
                  SUM(v.io_stall_write_ms) AS io_stall_write_ms,
                  MAX(CASE WHEN d.log_reuse_wait_desc='NOTHING' THEN 0 ELSE 1 END) AS log_reuse_blocked
                FROM sys.database_files f
                LEFT JOIN sys.dm_io_virtual_file_stats(DB_ID(),NULL) v ON v.file_id=f.file_id
                CROSS JOIN sys.dm_db_log_space_usage ls
                CROSS JOIN sys.databases d
                WHERE d.database_id=DB_ID()
                """;
    }
}
