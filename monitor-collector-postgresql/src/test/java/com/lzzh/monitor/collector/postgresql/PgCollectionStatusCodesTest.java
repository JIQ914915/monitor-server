package com.lzzh.monitor.collector.postgresql;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class PgCollectionStatusCodesTest {
    @Test
    void classifiesUserFriendlyFailureReasons() {
        assertThat(PgCollectionStatusCodes.reason(new SQLException("denied", "42501")))
                .isEqualTo(PgCollectionStatusCodes.PERMISSION_DENIED);
        assertThat(PgCollectionStatusCodes.reason(new SQLException("missing view", "42P01")))
                .isEqualTo(PgCollectionStatusCodes.UNSUPPORTED);
        assertThat(PgCollectionStatusCodes.reason(new SQLException("connection", "08006")))
                .isEqualTo(PgCollectionStatusCodes.CONNECTION_FAILED);
        assertThat(PgCollectionStatusCodes.reason(new SQLTimeoutException("slow")))
                .isEqualTo(PgCollectionStatusCodes.TIMEOUT);
    }
}
