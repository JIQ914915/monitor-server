package com.lzzh.monitor.collector.postgresql.item;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgTopSqlDeltaStoreTest {

    @Test
    void acceptsUnsigned64BitQueryIdAsFingerprintString() {
        PgTopSqlDeltaStore store = new PgTopSqlDeltaStore();
        String queryId = "10886475938161632000";

        assertThat(store.compute(1L, "postgres", queryId, 10, 100, 20, 1, 2, 0)).isNull();
        PgTopSqlDeltaStore.Delta delta = store.compute(
                1L, "postgres", queryId, 12, 140, 25, 2, 4, 1);

        assertThat(delta).isNotNull();
        assertThat(delta.deltaCalls()).isEqualTo(2);
        assertThat(delta.deltaExecMs()).isEqualTo(40);
    }

    @Test
    void extendedDeltaRebuildsBaselineAfterStatsReset() {
        PgTopSqlDeltaStore store = new PgTopSqlDeltaStore();
        var first = java.util.Map.of("shared_blks_read", 10d, "wal_bytes", 100d);
        var second = java.util.Map.of("shared_blks_read", 14d, "wal_bytes", 180d);

        assertThat(store.computeExtended(1L, "postgres", "42", 10, 100, 20, first,
                "2026-07-15T01:00:00Z")).isNull();
        PgTopSqlDeltaStore.ExtendedDelta delta = store.computeExtended(
                1L, "postgres", "42", 12, 140, 25, second, "2026-07-15T01:00:00Z");
        assertThat(delta).isNotNull();
        assertThat(delta.deltaCalls()).isEqualTo(2);
        assertThat(delta.metrics()).containsEntry("shared_blks_read", 4d)
                .containsEntry("wal_bytes", 80d);

        assertThat(store.computeExtended(1L, "postgres", "42", 1, 5, 1,
                java.util.Map.of("shared_blks_read", 1d), "2026-07-15T02:00:00Z")).isNull();
    }

    @Test
    void extendedDeltaNeverPublishesRolledBackCounters() {
        PgTopSqlDeltaStore store = new PgTopSqlDeltaStore();
        store.computeExtended(1L, "postgres", "42", 10, 100, 20,
                java.util.Map.of("temp_blks_written", 10d), "same-reset");

        PgTopSqlDeltaStore.ExtendedDelta delta = store.computeExtended(
                1L, "postgres", "42", 11, 120, 22,
                java.util.Map.of("temp_blks_written", 2d), "same-reset");

        assertThat(delta.metrics()).containsEntry("temp_blks_written", 0d);
    }}
