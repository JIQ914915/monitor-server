package com.lzzh.monitor.service.instance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerCapabilityProbeTest {
    private final SqlServerCapabilityProbe probe = new SqlServerCapabilityProbe();

    @Test
    void recognizesAllSupportedMajorVersions() {
        for (int major : new int[]{11, 12, 13, 14, 15, 16, 17}) {
            assertThat(probe.versionCapability(major, major + ".0", null).getStatus())
                    .as("major version %s", major)
                    .isEqualTo("available");
        }
        assertThat(probe.versionCapability(10, "10.0", null).getStatus())
                .isEqualTo("version_not_support");
    }

    @Test
    void explainsQueryStoreFallbackFor2012And2014() {
        assertThat(probe.versionCapability(11, "11.0", null).getMessage())
                .contains("不提供 Query Store", "DMV");
        assertThat(probe.versionCapability(12, "12.0", null).getMessage())
                .contains("不提供 Query Store", "DMV");
        assertThat(probe.versionCapability(13, "13.0", null).getMessage()).isNull();
    }
}
