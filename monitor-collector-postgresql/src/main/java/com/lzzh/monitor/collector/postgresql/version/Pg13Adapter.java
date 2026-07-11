package com.lzzh.monitor.collector.postgresql.version;

/**
 * PostgreSQL 13 适配器（13–16 通配基线）。
 * <p>只统计 client backend，排除 autovacuum、WAL sender 等后台进程对连接数的干扰。
 */
public class Pg13Adapter implements PgVersionAdapter {

    @Override
    public String version() {
        return "13";
    }

    @Override
    public String connectionsSql() {
        return """
                SELECT COUNT(*)                                                            AS total,
                       COUNT(*) FILTER (WHERE state = 'active')                            AS active,
                       COUNT(*) FILTER (WHERE state = 'idle')                              AS idle,
                       COUNT(*) FILTER (WHERE state LIKE 'idle in transaction%')           AS idle_in_trx,
                       COUNT(*) FILTER (WHERE wait_event_type = 'Lock')                    AS waiting
                  FROM pg_stat_activity
                 WHERE backend_type = 'client backend'
                """;
    }

    @Override
    public String databaseStatSql() {
        return """
                SELECT xact_commit, xact_rollback,
                       blks_read, blks_hit,
                       tup_returned, tup_fetched, tup_inserted, tup_updated, tup_deleted,
                       temp_files, temp_bytes, deadlocks
                  FROM pg_stat_database
                 WHERE datname = current_database()
                """;
    }

    @Override
    public String locksSql() {
        return """
                SELECT COUNT(*) FILTER (WHERE NOT granted)                                 AS waiting_locks,
                       (SELECT COUNT(*)
                          FROM pg_stat_activity
                         WHERE backend_type = 'client backend'
                           AND wait_event_type = 'Lock')                                   AS blocked_sessions
                  FROM pg_locks
                """;
    }

    @Override
    public String transactionsSql() {
        return """
                SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (now() - xact_start)))
                                FILTER (WHERE state <> 'idle'), 0)                          AS max_trx_seconds,
                       COUNT(*) FILTER (WHERE xact_start IS NOT NULL AND state <> 'idle')   AS active_trx,
                       COALESCE(MAX(EXTRACT(EPOCH FROM (now() - state_change)))
                                FILTER (WHERE state LIKE 'idle in transaction%'), 0)        AS idle_in_trx_max_seconds
                  FROM pg_stat_activity
                 WHERE backend_type = 'client backend'
                """;
    }

    @Override
    public String replicationSql() {
        return """
                SELECT pg_is_in_recovery()                                                  AS is_replica,
                       CASE WHEN pg_is_in_recovery()
                            THEN COALESCE(EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())), 0)
                            ELSE NULL END                                                   AS lag_seconds,
                       CASE WHEN pg_is_in_recovery() THEN NULL
                            ELSE (SELECT COUNT(*) FROM pg_stat_replication) END             AS replica_count
                """;
    }

    @Override
    public String capacitySql() {
        return """
                SELECT pg_database_size(current_database())                                 AS db_size_bytes,
                       (SELECT SUM(pg_database_size(oid)) FROM pg_database
                         WHERE NOT datistemplate)                                           AS total_size_bytes
                """;
    }
}
