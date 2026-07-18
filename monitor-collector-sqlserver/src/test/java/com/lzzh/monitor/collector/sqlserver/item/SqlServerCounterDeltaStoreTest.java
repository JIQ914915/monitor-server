package com.lzzh.monitor.collector.sqlserver.item;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerCounterDeltaStoreTest {
    private final SqlServerCounterDeltaStore store = new SqlServerCounterDeltaStore();

    @Test
    void emitsRateOnlyAfterBaseline() {
        assertThat(store.rate(1L, "batch", 100, 1_000)).isEmpty();
        assertThat(store.rate(1L, "batch", 160, 61_000)).hasValue(1.0);
    }

    @Test
    void resetsBaselineWhenCounterGoesBackwards() {
        store.rate(1L, "wait", 100, 1_000);
        assertThat(store.rate(1L, "wait", 10, 61_000)).isEmpty();
        assertThat(store.rate(1L, "wait", 40, 91_000)).hasValue(1.0);
    }

    @Test
    void emitsDeltaOnlyAfterBaselineAndIgnoresReset() {
        assertThat(store.delta(1L, "suspect", 2, 1_000)).isEmpty();
        assertThat(store.delta(1L, "suspect", 5, 61_000)).hasValue(3.0);
        assertThat(store.delta(1L, "suspect", 1, 121_000)).isEmpty();
    }
    @Test
    void keepsInstancesIsolated() {
        store.rate(1L, "io", 100, 1_000);
        assertThat(store.rate(2L, "io", 200, 61_000)).isEmpty();
        assertThat(store.rate(1L, "io", 220, 61_000)).hasValue(2.0);
    }
}
