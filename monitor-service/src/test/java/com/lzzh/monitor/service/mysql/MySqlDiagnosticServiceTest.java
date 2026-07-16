package com.lzzh.monitor.service.mysql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDiagnosticServiceTest {

    @Test
    void keepsBinlogRetentionUnknownWhenBothParametersAreMissing() {
        assertThat(MySqlDiagnosticService.binlogRetentionDays(null, null)).isNull();
    }

    @Test
    void prefersSecondsAndConvertsThemToDays() {
        assertThat(MySqlDiagnosticService.binlogRetentionDays(172800D, 7D)).isEqualTo(2D);
        assertThat(MySqlDiagnosticService.binlogRetentionDays(null, 7D)).isEqualTo(7D);
    }

    @Test
    void usesMinuteDataForWindowsUpToSixHours() {
        assertThat(MySqlDiagnosticService.correlationFrequency(0L, 6L * 3600_000)).isEqualTo("1m");
    }

    @Test
    void usesHourlyDataForWindowsLongerThanSixHours() {
        assertThat(MySqlDiagnosticService.correlationFrequency(0L, 6L * 3600_000 + 1)).isEqualTo("1h");
        assertThat(MySqlDiagnosticService.correlationFrequency(0L, 30L * 86400_000)).isEqualTo("1h");
    }
}
