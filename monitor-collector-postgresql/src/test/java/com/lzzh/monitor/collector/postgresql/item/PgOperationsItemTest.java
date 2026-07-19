package com.lzzh.monitor.collector.postgresql.item;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class PgOperationsItemTest {

    @Test
    void marksArchiveWarningOnlyWhenLatestArchiveAttemptFailed() {
        JSONObject recovered = new JSONObject();
        recovered.set("failed_count", 3);
        recovered.set("last_failed_time", "2026-07-15T10:00:00+08:00");
        recovered.set("last_archived_time", "2026-07-15T10:05:00+08:00");
        assertThat(PgOperationsItem.classifySeverity("wal_archiver", recovered, null)).isEqualTo("info");

        JSONObject failing = new JSONObject();
        failing.set("last_failed_time", "2026-07-15T10:10:00+08:00");
        failing.set("last_archived_time", "2026-07-15T10:05:00+08:00");
        assertThat(PgOperationsItem.classifySeverity("wal_archiver", failing, null)).isEqualTo("warning");
    }

    @Test
    void explainsUnsupportedAndPermissionFailures() {
        assertThat(PgOperationsItem.unavailableReason(new SQLException("missing view", "42P01")))
                .isEqualTo("unsupported");
        assertThat(PgOperationsItem.unavailableReason(new SQLException("missing column", "42703")))
                .isEqualTo("unsupported");
        assertThat(PgOperationsItem.unavailableReason(new SQLException("denied", "42501")))
                .isEqualTo("permission_denied");
        assertThat(PgOperationsItem.unavailableReason(new SQLException("network", "08006")))
                .isEqualTo("connection_failed");
    }
}