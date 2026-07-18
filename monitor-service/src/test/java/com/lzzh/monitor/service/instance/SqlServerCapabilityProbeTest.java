package com.lzzh.monitor.service.instance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerCapabilityProbeTest {
    private final SqlServerCapabilityProbe probe = new SqlServerCapabilityProbe();

    @Test
    void recognizesAllSupportedMajorVersions() {
        for (int major : new int[]{10, 11, 12, 13, 14, 15, 16, 17}) {
            assertThat(probe.versionCapability(major, major + ".0", null).getStatus())
                    .as("major version %s", major)
                    .isEqualTo("available");
        }
        assertThat(probe.versionCapability(9, "9.0", null).getStatus())
                .isEqualTo("version_not_support");
    }

    @Test
    void distinguishesUnsupportedDisabledAndEditionCapabilities() {
        assertThat(probe.alwaysOnCapability(10, 0).getStatus()).isEqualTo("version_not_support");
        assertThat(probe.alwaysOnCapability(13, 0).getStatus()).isEqualTo("not_enabled");
        assertThat(probe.alwaysOnCapability(13, 1).getStatus()).isEqualTo("available");
        assertThat(probe.agentCapability("Express Edition", 4).getStatus()).isEqualTo("edition_not_support");
        assertThat(probe.agentCapability("Standard Edition", 2).getStatus()).isEqualTo("available");
    }
    @Test
    void explainsLegacyFeatureFallback() {
        assertThat(probe.versionCapability(10, "10.50", null).getMessage())
                .contains("不提供 Query Store", "Always On", "DMV");
        assertThat(probe.versionCapability(11, "11.0", null).getMessage())
                .contains("不提供 Query Store", "DMV");
        assertThat(probe.versionCapability(12, "12.0", null).getMessage())
                .contains("不提供 Query Store", "DMV");
        assertThat(probe.versionCapability(13, "13.0", null).getMessage()).isNull();
        assertThat(SqlServerCapabilityProbe.majorFromProductVersion("10.50.6000.34")).isEqualTo(10);
        assertThat(SqlServerCapabilityProbe.majorFromProductVersion(null)).isZero();
    }
}
