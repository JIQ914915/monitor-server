package com.lzzh.monitor.service.instance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlCapabilityProbeTest {
    @Test void explicitlySupportsDeclaredVersions() {
        assertThat(MySqlCapabilityProbe.supported("5.6.51")).isTrue();
        assertThat(MySqlCapabilityProbe.supported("5.7.44-log")).isTrue();
        assertThat(MySqlCapabilityProbe.supported("8.0.42")).isTrue();
        assertThat(MySqlCapabilityProbe.supported("8.4.6-commercial")).isTrue();
        assertThat(MySqlCapabilityProbe.supported("9.0.1")).isFalse();
    }

    @Test void appliesFeaturePatchContracts() {
        assertThat(MySqlCapabilityProbe.atLeast("8.0.22", 8, 0, 22)).isTrue();
        assertThat(MySqlCapabilityProbe.atLeast("8.0.21", 8, 0, 22)).isFalse();
        assertThat(MySqlCapabilityProbe.atLeast("8.4.0", 8, 0, 22)).isTrue();
    }
}
