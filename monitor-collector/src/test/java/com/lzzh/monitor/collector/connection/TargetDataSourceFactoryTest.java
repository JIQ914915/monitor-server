package com.lzzh.monitor.collector.connection;

import com.lzzh.monitor.api.response.CollectTargetVo;
import com.lzzh.monitor.collector.spi.model.TargetDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetDataSourceFactoryTest {

    @Test
    void usesConfiguredDriverWithoutCheckingDatabaseType() {
        CollectTargetVo target = target("org.example.CustomDriver");

        TargetDataSource dataSource = TargetDataSourceFactory.from(target);

        assertThat(dataSource.getDriverClass()).isEqualTo("org.example.CustomDriver");
        assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:test://127.0.0.1:5432/monitor");
    }

    @Test
    void rejectsMissingConfiguredDriverInsteadOfGuessingFromDatabaseType() {
        CollectTargetVo target = target(null);

        assertThatThrownBy(() -> TargetDataSourceFactory.from(target))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("driver_class");
    }

    private CollectTargetVo target(String driverClass) {
        CollectTargetVo target = new CollectTargetVo();
        target.setId(1L);
        target.setHost("127.0.0.1");
        target.setPort(5432);
        target.setDatabaseName("monitor");
        target.setUrlTemplate("jdbc:test://{host}:{port}/{database}");
        target.setDriverClass(driverClass);
        return target;
    }
}
