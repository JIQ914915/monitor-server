package com.lzzh.monitor.collector.sqlserver.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerVersionResolverTest {
    private final SqlServerVersionResolver resolver = new SqlServerVersionResolver();

    @Test
    void resolvesEveryDeclaredVersionToAnExplicitAdapter() {
        assertThat(resolver.resolve("2017.0")).isExactlyInstanceOf(SqlServer2017Adapter.class);
        assertThat(resolver.resolve("2019.0")).isExactlyInstanceOf(SqlServer2019Adapter.class);
        assertThat(resolver.resolve("2022.0")).isExactlyInstanceOf(SqlServer2022Adapter.class);
        assertThat(resolver.resolve("2025.0")).isExactlyInstanceOf(SqlServer2025Adapter.class);
    }

    @Test
    void rejectsUndeclaredVersions() {
        assertThat(resolver.resolve("2016")).isNull();
        assertThat(resolver.resolve("2030")).isNull();
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void adaptersExposeReadOnlyContractQueries() {
        for (String version : new String[]{"2017", "2019", "2022", "2025"}) {
            SqlServerVersionAdapter adapter = resolver.resolve(version);
            assertThat(adapter.identitySql()).contains("SERVERPROPERTY", "sys.dm_os_sys_info");
            assertThat(adapter.queryStoreCapabilitySql())
                    .contains("sys.database_query_store_options")
                    .doesNotContain("ALTER ", "EXEC ", "UPDATE ", "DELETE ");
        }
    }
}
