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


    @Override
    public String queryStoreTopSql() {
        return """
                SELECT TOP (50) DB_NAME() AS database_name,
                       CONVERT(varchar(64), q.query_hash, 2) AS digest,
                       LEFT(qt.query_sql_text, 2000) AS sql_text,
                       SUM(rs.count_executions) AS executions,
                       SUM(rs.avg_duration * rs.count_executions) AS duration_us,
                       SUM(rs.avg_logical_io_reads * rs.count_executions) AS logical_reads,
                       SUM(rs.avg_physical_io_reads * rs.count_executions) AS physical_reads,
                       SUM(rs.avg_logical_io_writes * rs.count_executions) AS writes,
                       SUM(rs.avg_rowcount * rs.count_executions) AS rows_count
                  FROM sys.query_store_query_text qt
                  JOIN sys.query_store_query q ON q.query_text_id=qt.query_text_id
                  JOIN sys.query_store_plan p ON p.query_id=q.query_id
                  JOIN sys.query_store_runtime_stats rs ON rs.plan_id=p.plan_id
                  JOIN sys.query_store_runtime_stats_interval i ON i.runtime_stats_interval_id=rs.runtime_stats_interval_id
                 WHERE i.end_time >= DATEADD(hour,-1,SYSUTCDATETIME())
                 GROUP BY q.query_hash, qt.query_sql_text
                 ORDER BY duration_us DESC
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
                       qs.total_logical_writes AS writes, qs.total_rows AS rows_count
                  FROM sys.dm_exec_query_stats qs
                  CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) st
                 ORDER BY qs.total_elapsed_time DESC
                """;
    }

    @Override
    public String deadlockEventsSql() {
        return """
                SELECT TOP (20)
                       x.event_data.value('(event/@timestamp)[1]','datetime2') AS event_time,
                       CAST(x.event_data.query('.') AS nvarchar(max)) AS event_xml
                  FROM (SELECT CAST(t.target_data AS xml) AS target_data
                          FROM sys.dm_xe_session_targets t
                          JOIN sys.dm_xe_sessions s ON s.address=t.event_session_address
                         WHERE s.name='system_health' AND t.target_name='ring_buffer') d
                  CROSS APPLY d.target_data.nodes('RingBufferTarget/event[@name="xml_deadlock_report"]') x(event_data)
                 ORDER BY event_time DESC
                """;
    }

    @Override
    public String blockingChainSql() {
        return """
                SELECT DATEDIFF(second,r.start_time,SYSDATETIME()) AS wait_age_secs,
                       COALESCE(OBJECT_SCHEMA_NAME(p.object_id,r.database_id)+'.'+OBJECT_NAME(p.object_id,r.database_id),
                                DB_NAME(r.database_id)) AS locked_table,
                       r.wait_type AS locked_type, r.session_id AS waiting_pid,
                       LEFT(wt.text,500) AS waiting_query, r.blocking_session_id AS blocking_pid,
                       LEFT(bt.text,500) AS blocking_query
                  FROM sys.dm_exec_requests r
                  OUTER APPLY sys.dm_exec_sql_text(r.sql_handle) wt
                  LEFT JOIN sys.dm_exec_requests br ON br.session_id=r.blocking_session_id
                  OUTER APPLY sys.dm_exec_sql_text(br.sql_handle) bt
                  LEFT JOIN sys.dm_tran_locks l ON l.request_session_id=r.session_id
                  LEFT JOIN sys.partitions p ON p.hobt_id=l.resource_associated_entity_id
                 WHERE r.blocking_session_id>0 AND r.session_id<>@@SPID
                 ORDER BY wait_age_secs DESC
                """;
    }


    @Override
    public String backupCoverageSql() {
        return """
                SELECT d.database_id,d.name AS database_name,d.recovery_model_desc,
                       DATEDIFF(hour,MAX(CASE WHEN b.type='D' THEN b.backup_finish_date END),GETDATE()) AS full_age_hours,
                       DATEDIFF(hour,MAX(CASE WHEN b.type='I' THEN b.backup_finish_date END),GETDATE()) AS diff_age_hours,
                       DATEDIFF(minute,MAX(CASE WHEN b.type='L' THEN b.backup_finish_date END),GETDATE()) AS log_age_minutes,
                       MAX(CASE WHEN b.rn=1 THEN DATEDIFF(second,b.backup_start_date,b.backup_finish_date) END) latest_duration_seconds,
                       MAX(CASE WHEN b.rn=1 THEN b.backup_size END) latest_size_bytes,
                       MAX(CASE WHEN b.rn=1 THEN b.compressed_backup_size END) latest_compressed_bytes,
                       MAX(CASE WHEN b.rn=1 AND b.has_backup_checksums=1 THEN 1 ELSE 0 END) latest_has_checksum
                  FROM sys.databases d
                  LEFT JOIN (
                    SELECT *,ROW_NUMBER() OVER(PARTITION BY database_name ORDER BY backup_finish_date DESC) rn
                      FROM msdb.dbo.backupset WHERE is_copy_only IN (0,1)
                  ) b ON b.database_name=d.name
                 WHERE d.state_desc='ONLINE' AND d.source_database_id IS NULL
                 GROUP BY d.database_id,d.name,d.recovery_model_desc
                 ORDER BY d.name
                """;
    }

    @Override
    public String alwaysOnHealthSql() {
        return """
                SELECT ag.name AS group_name,ar.replica_server_name,DB_NAME(drs.database_id) AS database_name,
                       ars.role_desc,ars.connected_state_desc,ars.operational_state_desc,
                       drs.synchronization_state_desc,drs.synchronization_health_desc,
                       drs.is_suspended,drs.suspend_reason_desc,drs.log_send_queue_size,
                       drs.log_send_rate,drs.redo_queue_size,drs.redo_rate,
                       CASE WHEN drs.log_send_rate>0 THEN drs.log_send_queue_size*1.0/drs.log_send_rate END AS send_seconds,
                       CASE WHEN drs.redo_rate>0 THEN drs.redo_queue_size*1.0/drs.redo_rate END AS redo_seconds
                  FROM sys.dm_hadr_database_replica_states drs
                  JOIN sys.availability_replicas ar ON ar.replica_id=drs.replica_id
                  JOIN sys.availability_groups ag ON ag.group_id=ar.group_id
                  LEFT JOIN sys.dm_hadr_availability_replica_states ars ON ars.replica_id=ar.replica_id
                         AND ars.group_id=ag.group_id
                 WHERE drs.is_local=1
                """;
    }
}