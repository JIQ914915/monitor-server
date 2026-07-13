package com.lzzh.monitor.collector.mysql.item;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TopSqlDeltaStoreTest {

    @Test
    void computesDeltaWhenUnsignedCountersExceedLongMaxValue() {
        TopSqlDeltaStore store = new TopSqlDeltaStore();
        BigInteger timer = new BigInteger("10925463552029938000");
        BigInteger lock = new BigInteger("10886475938161632000");

        assertThat(store.compute(1L, "app", "digest", bi(10), timer, bi(100), bi(50),
                lock, bi(1), bi(2), bi(3), bi(4))).isNull();

        TopSqlDeltaStore.Delta delta = store.compute(1L, "app", "digest", bi(12),
                timer.add(bi(2_000_000_000L)), bi(120), bi(60),
                lock.add(bi(500_000_000L)), bi(2), bi(3), bi(5), bi(6));

        assertThat(delta).isNotNull();
        assertThat(delta.deltaCount()).isEqualTo(2);
        assertThat(delta.deltaTimerWait()).isEqualTo(2_000_000_000L);
        assertThat(delta.avgTimerWaitUs()).isEqualTo(1_000L);
        assertThat(delta.deltaLockTime()).isEqualTo(500_000_000L);
        assertThat(TopSqlDeltaStore.toSignedLongSaturated(timer)).isEqualTo(Long.MAX_VALUE);
    }

    private static BigInteger bi(long value) {
        return BigInteger.valueOf(value);
    }
}