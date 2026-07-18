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
                  MAX(CASE WHEN object_name LIKE '%:General Statistics'
                            AND counter_name='User Connections' AND cntr_type=65792
                           THEN cntr_value END) AS user_connections,
                  MAX(CASE WHEN object_name LIKE '%:SQL Statistics'
                            AND counter_name='Batch Requests/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS batch_requests_total,
                  MAX(CASE WHEN object_name LIKE '%:SQL Statistics'
                            AND counter_name='SQL Compilations/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS compilations_total,
                  MAX(CASE WHEN object_name LIKE '%:SQL Statistics'
                            AND counter_name='SQL Re-Compilations/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS recompilations_total,
                  MAX(CASE WHEN object_name LIKE '%:Memory Manager'
                            AND counter_name='Memory Grants Pending' AND cntr_type=65792
                           THEN cntr_value END) AS memory_grants_pending,
                  MAX(CASE WHEN object_name LIKE '%:Memory Manager'
                            AND counter_name='Memory Grants Outstanding' AND cntr_type=65792
                           THEN cntr_value END) AS memory_grants_outstanding,
                  MAX(CASE WHEN object_name LIKE '%:Memory Manager'
                            AND counter_name='Total Server Memory (KB)' AND cntr_type=65792
                           THEN cntr_value END) AS total_server_memory_kb,
                  MAX(CASE WHEN object_name LIKE '%:Memory Manager'
                            AND counter_name='Target Server Memory (KB)' AND cntr_type=65792
                           THEN cntr_value END) AS target_server_memory_kb,
                  MAX(CASE WHEN object_name LIKE '%:Buffer Manager'
                            AND counter_name='Page life expectancy' AND cntr_type=65792
                           THEN cntr_value END) AS page_life_expectancy,
                  MAX(CASE WHEN object_name LIKE '%:Buffer Manager'
                            AND counter_name='Lazy writes/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS lazy_writes_total,
                  MAX(CASE WHEN object_name LIKE '%:Buffer Manager'
                            AND counter_name='Page reads/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS page_reads_total,
                  MAX(CASE WHEN object_name LIKE '%:Buffer Manager'
                            AND counter_name='Page writes/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS page_writes_total,
                  MAX(CASE WHEN object_name LIKE '%:Locks' AND instance_name='_Total'
                            AND counter_name='Number of Deadlocks/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS deadlocks_total,
                  MAX(CASE WHEN object_name LIKE '%:Databases' AND instance_name='_Total'
                            AND counter_name='Log Bytes Flushed/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS log_bytes_flushed_total,
                  MAX(CASE WHEN object_name LIKE '%:Databases' AND instance_name='_Total'
                            AND counter_name='Log Flushes/sec' AND cntr_type IN (272696320,272696576)
                           THEN cntr_value END) AS log_flushes_total,
                  MAX(CASE WHEN object_name LIKE '%:Databases' AND instance_name='_Total'
                            AND counter_name='Log Flush Wait Time' AND cntr_type IN (65792,272696320,272696576)
                           THEN cntr_value END) AS log_flush_wait_ms_total
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
    public String transactionDetailSql() {
        return """
                WITH transactions AS (
                  SELECT s.session_id,s.status,s.login_name,s.host_name,s.program_name,
                         DB_NAME(dt.database_id) AS database_name,
                         DATEDIFF(second,at.transaction_begin_time,SYSDATETIME()) AS transaction_seconds,
                         s.open_transaction_count,
                         CASE WHEN s.status='sleeping' AND s.open_transaction_count>0 THEN 1 ELSE 0 END AS sleeping_open,
                         LEFT(txt.text,500) AS sql_text,
                         ROW_NUMBER() OVER(PARTITION BY s.session_id,at.transaction_id
                                           ORDER BY dt.database_id) AS rn
                    FROM sys.dm_tran_session_transactions st
                    JOIN sys.dm_tran_active_transactions at ON at.transaction_id=st.transaction_id
                    JOIN sys.dm_exec_sessions s ON s.session_id=st.session_id
                    LEFT JOIN sys.dm_exec_connections c ON c.session_id=s.session_id
                    LEFT JOIN sys.dm_tran_database_transactions dt ON dt.transaction_id=at.transaction_id
                    OUTER APPLY sys.dm_exec_sql_text(c.most_recent_sql_handle) txt
                   WHERE s.is_user_process=1 AND s.session_id<>@@SPID
                     AND at.transaction_begin_time IS NOT NULL
                )
                SELECT session_id,status,login_name,host_name,program_name,database_name,
                       transaction_seconds,open_transaction_count,sleeping_open,sql_text
                  FROM transactions WHERE rn=1
                 ORDER BY transaction_seconds DESC
                """;
    }

    @Override
    public String waitStatsSql() {
        return """
                SELECT wait_type, wait_time_ms, signal_wait_time_ms, waiting_tasks_count
                  FROM sys.dm_os_wait_stats
                 WHERE wait_type NOT IN ('SLEEP_TASK','SLEEP_SYSTEMTASK','LAZYWRITER_SLEEP',
                   'SQLTRACE_BUFFER_FLUSH','XE_TIMER_EVENT','XE_DISPATCHER_WAIT',
                   'REQUEST_FOR_DEADLOCK_SEARCH','BROKER_TO_FLUSH','BROKER_TASK_STOP')
                """;
    }

    @Override
    public String databaseHealthSql() {
        return """
                SELECT database_id, name AS database_name, state, state_desc, user_access, user_access_desc,
                       is_read_only, recovery_model, recovery_model_desc
                  FROM sys.databases
                 WHERE database_id > 4 AND source_database_id IS NULL
                 ORDER BY name
                """;
    }

    @Override
    public String suspectPagesSql() {
        return """
                SELECT database_id, DB_NAME(database_id) AS database_name, file_id, page_id, event_type, error_count, last_update_date
                  FROM msdb.dbo.suspect_pages
                 WHERE event_type IN (1,2,3,4)
                 ORDER BY last_update_date DESC
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
                  MAX(d.log_reuse_wait_desc) AS log_reuse_wait_desc
                FROM sys.database_files f
                LEFT JOIN sys.dm_io_virtual_file_stats(DB_ID(),NULL) v ON v.file_id=f.file_id
                CROSS JOIN sys.dm_db_log_space_usage ls
                CROSS JOIN sys.databases d
                WHERE d.database_id=DB_ID()
                """;
    }


    @Override
    public String fileCapacitySql() {
        return """
                SELECT DB_NAME() AS database_name,f.file_id,f.name AS file_name,f.type_desc,f.physical_name,
                       f.size*8192.0 AS size_bytes,
                       CASE WHEN f.type=0 THEN FILEPROPERTY(f.name,'SpaceUsed')*8192.0 END AS used_bytes,
                       CASE WHEN f.max_size=-1 THEN NULL ELSE f.max_size*8192.0 END AS max_size_bytes,
                       f.is_percent_growth,
                       CASE WHEN f.is_percent_growth=1 THEN f.growth ELSE f.growth*8192.0 END AS growth_value,
                       vs.volume_mount_point,vs.total_bytes AS volume_total_bytes,
                       vs.available_bytes AS volume_available_bytes
                  FROM sys.database_files f
                  OUTER APPLY sys.dm_os_volume_stats(DB_ID(),f.file_id) vs
                 ORDER BY f.file_id
                """;
    }

    @Override
    public String vlfSql() {
        return """
                SELECT COUNT(*) AS vlf_count,
                       SUM(CASE WHEN vlf_active=1 THEN 1 ELSE 0 END) AS active_vlf_count
                  FROM sys.dm_db_log_info(DB_ID())
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
    public String queryStoreRegressionSql() {
        return """
                WITH plan_stats AS (
                  SELECT q.query_hash,p.plan_id,
                         SUM(CASE WHEN i.end_time>=DATEADD(hour,-1,SYSUTCDATETIME())
                                  THEN rs.count_executions ELSE 0 END) AS current_executions,
                         SUM(CASE WHEN i.end_time>=DATEADD(hour,-1,SYSUTCDATETIME())
                                  THEN rs.avg_duration*rs.count_executions ELSE 0 END)/
                           NULLIF(SUM(CASE WHEN i.end_time>=DATEADD(hour,-1,SYSUTCDATETIME())
                                           THEN rs.count_executions ELSE 0 END),0) AS current_avg_duration_us,
                         SUM(CASE WHEN i.end_time<DATEADD(hour,-1,SYSUTCDATETIME())
                                  THEN rs.count_executions ELSE 0 END) AS baseline_executions,
                         SUM(CASE WHEN i.end_time<DATEADD(hour,-1,SYSUTCDATETIME())
                                  THEN rs.avg_duration*rs.count_executions ELSE 0 END)/
                           NULLIF(SUM(CASE WHEN i.end_time<DATEADD(hour,-1,SYSUTCDATETIME())
                                           THEN rs.count_executions ELSE 0 END),0) AS baseline_avg_duration_us
                    FROM sys.query_store_query q
                    JOIN sys.query_store_plan p ON p.query_id=q.query_id
                    JOIN sys.query_store_runtime_stats rs ON rs.plan_id=p.plan_id
                    JOIN sys.query_store_runtime_stats_interval i
                      ON i.runtime_stats_interval_id=rs.runtime_stats_interval_id
                   WHERE i.end_time>=DATEADD(day,-8,SYSUTCDATETIME())
                   GROUP BY q.query_hash,p.plan_id
                ), query_summary AS (
                  SELECT query_hash,SUM(current_executions) AS current_executions,
                         COUNT(CASE WHEN current_executions>0 THEN 1 END) AS current_plan_count,
                         MAX(CASE WHEN current_executions>0 AND baseline_executions=0 THEN 1 ELSE 0 END) AS has_new_plan,
                         MAX(CASE WHEN baseline_avg_duration_us>0 AND current_avg_duration_us IS NOT NULL
                                  THEN current_avg_duration_us/baseline_avg_duration_us END) AS regression_ratio
                    FROM plan_stats GROUP BY query_hash
                )
                SELECT TOP (50) CONVERT(varchar(64),query_hash,2) AS digest,current_executions,
                       current_plan_count,has_new_plan,regression_ratio
                  FROM query_summary WHERE current_executions>0
                 ORDER BY regression_ratio DESC,current_executions DESC
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
                SELECT ag.name AS group_name,ar.replica_server_name,adc.database_name,
                       drs.is_local,ars.role_desc,ars.connected_state_desc,ars.operational_state_desc,
                       drs.synchronization_state_desc,drs.synchronization_health_desc,
                       drs.is_suspended,drs.suspend_reason_desc,drs.log_send_queue_size,
                       drs.log_send_rate,drs.redo_queue_size,drs.redo_rate,
                       CASE WHEN drs.log_send_rate>0 THEN drs.log_send_queue_size*1.0/drs.log_send_rate END AS send_seconds,
                       CASE WHEN drs.redo_rate>0 THEN drs.redo_queue_size*1.0/drs.redo_rate END AS redo_seconds
                  FROM sys.dm_hadr_database_replica_states drs
                  JOIN sys.availability_replicas ar ON ar.replica_id=drs.replica_id
                  JOIN sys.availability_groups ag ON ag.group_id=ar.group_id
                  JOIN sys.availability_databases_cluster adc ON adc.group_id=ag.group_id
                       AND adc.group_database_id=drs.group_database_id
                  LEFT JOIN sys.dm_hadr_availability_replica_states ars ON ars.replica_id=ar.replica_id
                         AND ars.group_id=ag.group_id
                ORDER BY ag.name, ar.replica_server_name, database_name
                """;
    }


    @Override
    public String agentHealthSql() {
        return """
                WITH last_run AS (
                  SELECT job_id,run_status,run_duration,message,
                         ROW_NUMBER() OVER(PARTITION BY job_id ORDER BY instance_id DESC) rn
                    FROM msdb.dbo.sysjobhistory WHERE step_id=0
                )
                SELECT (SELECT COUNT(*) FROM msdb.dbo.sysjobs) AS job_count,
                       SUM(CASE WHEN j.enabled=0 THEN 1 ELSE 0 END) AS disabled_jobs,
                       SUM(CASE WHEN l.run_status=0 THEN 1 ELSE 0 END) AS failed_jobs,
                       SUM(CASE WHEN a.start_execution_date IS NOT NULL AND a.stop_execution_date IS NULL THEN 1 ELSE 0 END) AS running_jobs,
                       MAX(CASE WHEN l.run_status=0 THEN l.run_duration ELSE 0 END) AS max_failed_duration,
                       MAX(CASE WHEN l.run_status=0 THEN 1 ELSE 0 END) AS has_failure
                  FROM msdb.dbo.sysjobs j
                  LEFT JOIN last_run l ON l.job_id=j.job_id AND l.rn=1
                  LEFT JOIN msdb.dbo.sysjobactivity a ON a.job_id=j.job_id
                    AND a.session_id=(SELECT MAX(session_id) FROM msdb.dbo.syssessions)
                """;
    }

    @Override
    public String agentJobsSql() {
        return """
                WITH last_run AS (
                  SELECT job_id,run_status,run_date,run_time,run_duration,step_name,
                         ROW_NUMBER() OVER(PARTITION BY job_id ORDER BY instance_id DESC) rn
                    FROM msdb.dbo.sysjobhistory WHERE step_id=0
                ), activity AS (
                  SELECT job_id,start_execution_date,stop_execution_date,
                         ROW_NUMBER() OVER(PARTITION BY job_id ORDER BY session_id DESC) rn
                    FROM msdb.dbo.sysjobactivity
                )
                SELECT j.job_id,j.name AS job_name,j.enabled,
                       CASE WHEN a.start_execution_date IS NOT NULL AND a.stop_execution_date IS NULL
                            THEN 4 ELSE COALESCE(l.run_status,5) END AS status_code,
                       l.run_date,l.run_time,l.run_duration,
                       COALESCE((SELECT COUNT(*) FROM msdb.dbo.sysjobhistory f
                                  WHERE f.job_id=j.job_id AND f.step_id=0 AND f.run_status=0
                                    AND f.instance_id>COALESCE((SELECT MAX(s.instance_id)
                                          FROM msdb.dbo.sysjobhistory s
                                         WHERE s.job_id=j.job_id AND s.step_id=0 AND s.run_status=1),0)),0)
                         AS consecutive_failures,
                       DATEDIFF(second,a.start_execution_date,GETDATE()) AS running_seconds,
                       js.next_run_date,js.next_run_time,
                       failed.step_name AS failed_step_name
                  FROM msdb.dbo.sysjobs j
                  LEFT JOIN last_run l ON l.job_id=j.job_id AND l.rn=1
                  LEFT JOIN activity a ON a.job_id=j.job_id AND a.rn=1
                  OUTER APPLY (SELECT TOP (1) s.next_run_date,s.next_run_time
                                 FROM msdb.dbo.sysjobschedules s
                                WHERE s.job_id=j.job_id
                                ORDER BY CASE WHEN s.next_run_date=0 THEN 1 ELSE 0 END,
                                         s.next_run_date,s.next_run_time) js
                  OUTER APPLY (SELECT TOP (1) h.step_name
                                FROM msdb.dbo.sysjobhistory h
                               WHERE h.job_id=j.job_id AND h.step_id>0 AND h.run_status=0
                               ORDER BY h.instance_id DESC) failed
                 ORDER BY j.name
                """;
    }

    @Override
    public String logShippingSql() {
        return """
                SELECT
                  (SELECT COUNT(*) FROM msdb.dbo.log_shipping_monitor_primary) AS primary_count,
                  (SELECT COUNT(*) FROM msdb.dbo.log_shipping_monitor_secondary) AS secondary_count,
                  (SELECT COALESCE(MAX(DATEDIFF(minute,last_backup_date,GETDATE())),0)
                     FROM msdb.dbo.log_shipping_monitor_primary) AS max_backup_delay_minutes,
                  (SELECT COALESCE(MAX(DATEDIFF(minute,last_copied_date,GETDATE())),0)
                     FROM msdb.dbo.log_shipping_monitor_secondary) AS max_copy_delay_minutes,
                  (SELECT COALESCE(MAX(DATEDIFF(minute,last_restored_date,GETDATE())),0)
                     FROM msdb.dbo.log_shipping_monitor_secondary) AS max_restore_delay_minutes
                """;
    }

    @Override
    public String replicationCdcSql() {
        return """
                SELECT SUM(CASE WHEN is_published=1 OR is_merge_published=1 THEN 1 ELSE 0 END) published_databases,
                       SUM(CASE WHEN is_subscribed=1 THEN 1 ELSE 0 END) subscribed_databases,
                       SUM(CASE WHEN is_cdc_enabled=1 THEN 1 ELSE 0 END) cdc_databases
                  FROM sys.databases
                """;
    }

    @Override
    public String replicationLatencySql() {
        return """
                SELECT object_name,counter_name,instance_name,cntr_value
                  FROM sys.dm_os_performance_counters
                 WHERE (object_name LIKE '%:Replication Dist.%'
                    OR object_name LIKE '%:Replication Logreader%')
                   AND counter_name='Delivery Latency'
                """;
    }

    @Override
    public String cdcLatencySql() {
        return """
                SELECT COUNT(*) AS capture_instance_count,
                       DATEDIFF(second,sys.fn_cdc_map_lsn_to_time(sys.fn_cdc_get_max_lsn()),GETDATE())
                         AS cdc_latency_seconds
                  FROM cdc.change_tables
                """;
    }

    @Override
    public String configurationSnapshotSql() {
        return """
                SELECT name,CAST(value_in_use AS nvarchar(256)) value_text,'instance' scope_name
                  FROM sys.configurations
                 WHERE name IN ('max server memory (MB)','max degree of parallelism',
                    'cost threshold for parallelism','backup compression default','optimize for ad hoc workloads')
                UNION ALL
                SELECT 'database.compatibility_level',CAST(compatibility_level AS nvarchar(256)),name
                  FROM sys.databases WHERE database_id>4
                UNION ALL
                SELECT 'database.auto_shrink',CAST(is_auto_shrink_on AS nvarchar(256)),name
                  FROM sys.databases WHERE database_id>4
                UNION ALL
                SELECT 'database.recovery_model',recovery_model_desc,name
                  FROM sys.databases WHERE database_id>4
                ORDER BY scope_name,name
                """;
    }

    @Override
    public String indexCandidatesSql() {
        return """
                SELECT TOP (50) 'missing' candidate_type,
                       COALESCE(OBJECT_SCHEMA_NAME(mid.object_id,mid.database_id),'')+'.'+
                       COALESCE(OBJECT_NAME(mid.object_id,mid.database_id),'') object_name,
                       CAST(migs.avg_total_user_cost*migs.avg_user_impact*(migs.user_seeks+migs.user_scans) AS float) score
                  FROM sys.dm_db_missing_index_group_stats migs
                  JOIN sys.dm_db_missing_index_groups mig ON mig.index_group_handle=migs.group_handle
                  JOIN sys.dm_db_missing_index_details mid ON mid.index_handle=mig.index_handle
                 WHERE mid.database_id=DB_ID()
                UNION ALL
                SELECT TOP (50) 'unused',OBJECT_SCHEMA_NAME(i.object_id)+'.'+OBJECT_NAME(i.object_id)+'.'+i.name,
                       CAST(SUM(ps.used_page_count)*8.0 AS float)
                  FROM sys.indexes i JOIN sys.dm_db_partition_stats ps ON ps.object_id=i.object_id AND ps.index_id=i.index_id
                  LEFT JOIN sys.dm_db_index_usage_stats u ON u.database_id=DB_ID() AND u.object_id=i.object_id AND u.index_id=i.index_id
                 WHERE i.index_id>1 AND i.is_primary_key=0 AND i.is_unique_constraint=0
                   AND COALESCE(u.user_seeks,0)+COALESCE(u.user_scans,0)+COALESCE(u.user_lookups,0)=0
                 GROUP BY i.object_id,i.name
                 ORDER BY score DESC
                """;
    }
}