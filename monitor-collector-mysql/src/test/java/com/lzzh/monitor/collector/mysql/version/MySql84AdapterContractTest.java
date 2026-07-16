package com.lzzh.monitor.collector.mysql.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySql84AdapterContractTest {
    @Test void resolvesExplicit84Adapter() {
        MySqlVersionAdapter adapter = new MySqlVersionResolver().resolve("8.4.6");
        assertThat(adapter).isInstanceOf(MySql84Adapter.class);
        assertThat(adapter.version()).isEqualTo("8.4");
        assertThat(adapter.replicaStatusSql()).isEqualTo("SHOW REPLICA STATUS");
        assertThat(adapter.lockWaitsSql()).contains("performance_schema.data_lock_waits");
        assertThat(adapter.supportsPerformanceSchema()).isTrue();
        assertThat(adapter.hasErrorLogTable()).isTrue();
    }

    @Test void keepsSupportedVersionContracts() {
        MySqlVersionResolver resolver = new MySqlVersionResolver();
        assertThat(resolver.resolve("5.6.51")).isInstanceOf(MySql56Adapter.class);
        assertThat(resolver.resolve("5.7.44")).isInstanceOf(MySql57Adapter.class);
        assertThat(resolver.resolve("8.0.42")).isInstanceOf(MySql80Adapter.class);
        assertThat(resolver.resolve("8.4.6")).isInstanceOf(MySql84Adapter.class);
    }
}
