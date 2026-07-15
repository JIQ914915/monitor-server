package com.lzzh.monitor.collector.postgresql.version;

/** PostgreSQL 17 适配器：检查点统计迁移至 pg_stat_checkpointer。 */
public class Pg17Adapter extends Pg16Adapter {
    @Override public String version() { return "17"; }

    @Override
    public String checkpointSql() {
        return """
                SELECT c.num_timed AS timed, c.num_requested AS requested,
                       c.buffers_written AS buffers_checkpoint, b.buffers_clean,
                       COALESCE((SELECT SUM(writes) FROM pg_stat_io
                                  WHERE backend_type = 'client backend' AND object = 'relation'), 0)
                           AS buffers_backend
                  FROM pg_stat_checkpointer c CROSS JOIN pg_stat_bgwriter b
                """;
    }
}