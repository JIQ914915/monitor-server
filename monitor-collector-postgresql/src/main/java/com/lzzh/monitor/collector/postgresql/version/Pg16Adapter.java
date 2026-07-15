package com.lzzh.monitor.collector.postgresql.version;

/** PostgreSQL 16 适配器：启用 pg_stat_io 关系对象统计。 */
public class Pg16Adapter extends Pg13Adapter {
    @Override public String version() { return "16"; }

    @Override
    public String statIoSql() {
        return """
                SELECT COALESCE(SUM(reads), 0) AS reads,
                       COALESCE(SUM(writes), 0) AS writes,
                       COALESCE(SUM(extends), 0) AS extends,
                       COALESCE(SUM(read_time), 0) AS read_time_ms,
                       COALESCE(SUM(write_time), 0) AS write_time_ms,
                       NULL::bigint AS read_bytes, NULL::bigint AS write_bytes, NULL::bigint AS extend_bytes
                  FROM pg_stat_io
                 WHERE object = 'relation'
                """;
    }
}