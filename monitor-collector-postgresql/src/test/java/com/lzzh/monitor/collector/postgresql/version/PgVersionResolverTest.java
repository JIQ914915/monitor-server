package com.lzzh.monitor.collector.postgresql.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgVersionResolverTest {

    private final PgVersionResolver resolver = new PgVersionResolver();

    @Test
    void resolvesSupportedMajorVersionsToMatchingAdapters() {
        assertThat(resolver.resolve("14.12")).isExactlyInstanceOf(Pg14Adapter.class);
        assertThat(resolver.resolve("15.7")).isExactlyInstanceOf(Pg15Adapter.class);
        assertThat(resolver.resolve("16.3")).isInstanceOf(Pg16Adapter.class);
        assertThat(resolver.resolve("17.1")).isInstanceOf(Pg17Adapter.class);
        assertThat(resolver.resolve("18.0")).isInstanceOf(Pg18Adapter.class);
    }

    @Test
    void usesVersionSpecificCheckpointAndIoColumns() {
        assertThat(resolver.resolve("17").checkpointSql())
                .contains("pg_stat_checkpointer", "num_timed", "num_requested");
        assertThat(resolver.resolve("18").statIoSql())
                .contains("read_bytes", "write_bytes", "extend_bytes");
        assertThat(resolver.resolve("16").statIoSql())
                .contains("NULL::bigint AS read_bytes");
        assertThat(resolver.resolve("16").statIoSql()).contains("MAX(stats_reset) AS stats_reset");
        assertThat(resolver.resolve("18").statIoSql()).contains("MAX(stats_reset) AS stats_reset");
        for (String version : new String[] {"14", "15", "16", "17", "18"}) {
            assertThat(resolver.resolve(version).databaseStatSql()).contains("stats_reset");
        }

    }
    @Test
    void transactionAndReplicationSqlUseValidEpochKeywordForEverySupportedVersion() {
        for (String version : new String[] {"14", "15", "16", "17", "18"}) {
            PgVersionAdapter adapter = resolver.resolve(version);
            assertThat(adapter.transactionsSql()).contains("EXTRACT(EPOCH").doesNotContain("EoOCH");
            assertThat(adapter.replicationSql()).contains("EXTRACT(EPOCH").doesNotContain("EoOCH");
        }
    }
}
