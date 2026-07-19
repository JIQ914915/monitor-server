package com.lzzh.monitor.collector.postgresql.version;

/** PostgreSQL 18 适配器：使用 pg_stat_io 新增的字节级 I/O 统计。 */
public class Pg18Adapter extends Pg17Adapter {
    @Override public String version() { return "18"; }

    @Override
    public String statIoSql() {
        return """
                SELECT COALESCE(SUM(reads), 0) AS reads,
                       COALESCE(SUM(writes), 0) AS writes,
                       COALESCE(SUM(extends), 0) AS extends,
                       COALESCE(SUM(read_time), 0) AS read_time_ms,
                       COALESCE(SUM(write_time), 0) AS write_time_ms,
                       MAX(stats_reset) AS stats_reset,
                       COALESCE(SUM(read_bytes), 0) AS read_bytes,
                       COALESCE(SUM(write_bytes), 0) AS write_bytes,
                       COALESCE(SUM(extend_bytes), 0) AS extend_bytes
                  FROM pg_stat_io
                 WHERE object = 'relation'
                """;
    }
}