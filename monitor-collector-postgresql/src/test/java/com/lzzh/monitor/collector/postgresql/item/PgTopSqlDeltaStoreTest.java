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
}
