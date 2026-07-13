package com.lzzh.monitor.service.alert;

import com.lzzh.monitor.dao.entity.AlertDrilldownProfile;
import com.lzzh.monitor.dao.mapper.AlertDrilldownProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlertDrilldownProfileServiceImplTest {

    @Test
    void matchesMetricOnlyWithinRequestedDatabaseType() {
        AlertDrilldownProfile mysql = profile(1L, "mysql-host", "mysql", "host.");
        AlertDrilldownProfile postgresql = profile(2L, "pg-host", "postgresql", "host.");
        AlertDrilldownProfileMapper mapper = mock(AlertDrilldownProfileMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(mysql, postgresql));

        AlertDrilldownProfileServiceImpl service = new AlertDrilldownProfileServiceImpl();
        ReflectionTestUtils.setField(service, "profileMapper", mapper);

        assertThat(service.match("host.disk.usage_max", "postgresql").getProfileLabel())
                .isEqualTo("pg-host");
    }

    @Test
    void usesGenericProfileOnlyWithinRequestedDatabaseType() {
        AlertDrilldownProfile mysql = genericProfile(1L, "mysql-generic", "mysql");
        AlertDrilldownProfile postgresql = genericProfile(2L, "pg-generic", "postgresql");
        AlertDrilldownProfileMapper mapper = mock(AlertDrilldownProfileMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(mysql, postgresql));

        AlertDrilldownProfileServiceImpl service = new AlertDrilldownProfileServiceImpl();
        ReflectionTestUtils.setField(service, "profileMapper", mapper);

        assertThat(service.match("unknown.metric", "postgresql").getProfileLabel())
                .isEqualTo("pg-generic");
    }
    @Test
    void doesNotMatchAcrossDatabaseTypesWhenTypeIsMissing() {
        AlertDrilldownProfileMapper mapper = mock(AlertDrilldownProfileMapper.class);
        AlertDrilldownProfileServiceImpl service = new AlertDrilldownProfileServiceImpl();
        ReflectionTestUtils.setField(service, "profileMapper", mapper);

        assertThat(service.match("host.disk.usage_max", null)).isNull();
        verifyNoInteractions(mapper);
    }

    private static AlertDrilldownProfile genericProfile(Long id, String label, String dbType) {
        AlertDrilldownProfile profile = profile(id, label, dbType, "unused.");
        profile.setMatchRules(List.of());
        return profile;
    }
    private static AlertDrilldownProfile profile(Long id, String label, String dbType, String prefix) {
        AlertDrilldownProfile profile = new AlertDrilldownProfile();
        profile.setId(id);
        profile.setProfileCode(label);
        profile.setProfileLabel(label);
        profile.setDbType(dbType);
        profile.setEnabled(true);
        profile.setMatchRules(List.of(Map.of("matchType", "prefix", "pattern", prefix)));
        profile.setRelatedMetrics(List.of());
        profile.setCauses(List.of());
        profile.setSteps(List.of());
        profile.setActions(List.of());
        return profile;
    }
}
