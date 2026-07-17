package com.lzzh.monitor.collector.sqlserver.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerVersionResolverTest {
    private final SqlServerVersionResolver resolver = new SqlServerVersionResolver();

    @Test
    void resolvesEveryDeclaredVersionToAnExplicitAdapter() {
        assertThat(resolver.resolve("2008R2")).isExactlyInstanceOf(SqlServer2008R2Adapter.class);
        assertThat(resolver.resolve("2012.0")).isExactlyInstanceOf(SqlServer2012Adapter.class);
        assertThat(resolver.resolve("2014.0")).isExactlyInstanceOf(SqlServer2014Adapter.class);
        assertThat(resolver.resolve("2016.0")).isExactlyInstanceOf(SqlServer2016Adapter.class);
        assertThat(resolver.resolve("2017.0")).isExactlyInstanceOf(SqlServer2017Adapter.class);
        assertThat(resolver.resolve("2019.0")).isExactlyInstanceOf(SqlServer2019Adapter.class);
        assertThat(resolver.resolve("2022.0")).isExactlyInstanceOf(SqlServer2022Adapter.class);
        assertThat(resolver.resolve("2025.0")).isExactlyInstanceOf(SqlServer2025Adapter.class);
    }

    @Test
    void rejectsUndeclaredVersions() {
        assertThat(resolver.resolve("2008")).isNull();
        assertThat(resolver.resolve("2030")).isNull();
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void legacyAdaptersUseDmvFallbackWithoutUnsupportedSql() {
        for (String version : new String[]{"2008R2", "2012", "2014"}) {
            SqlServerVersionAdapter adapter = resolver.resolve(version);
            assertThat(adapter.supportsQueryStore()).isFalse();
            assertThat(adapter.identitySql()).contains("DATEDIFF(second").doesNotContain("DATEDIFF_BIG");
            assertThat(adapter.queryStoreCapabilitySql()).doesNotContain("sys.database_query_store_options");
            assertThat(adapter.queryStoreTopSql()).doesNotContain("sys.query_store_");
            assertThat(adapter.dmvTopSql()).contains("sys.dm_exec_query_stats");
        }
        SqlServerVersionAdapter sql2008 = resolver.resolve("2008R2");
        assertThat(sql2008.supportsAlwaysOn()).isFalse();
        assertThat(sql2008.storageSql()).contains("sys.dm_os_performance_counters")
                .doesNotContain("sys.dm_db_log_space_usage");
        assertThat(sql2008.dmvTopSql()).contains("CAST(0 AS bigint) AS rows_count")
                .doesNotContain("qs.total_rows");
        assertThat(resolver.resolve("2016").supportsQueryStore()).isTrue();
    }

    @Test
    void adaptersExposeReadOnlyContractQueries() {
        for (String version : new String[]{"2008R2", "2012", "2014", "2016", "2017", "2019", "2022", "2025"}) {
            SqlServerVersionAdapter adapter = resolver.resolve(version);
            assertThat(adapter.identitySql()).contains("SERVERPROPERTY", "sys.dm_os_sys_info");
            assertThat(adapter.performanceCountersSql())
                    .contains("sys.dm_os_performance_counters", "Memory Grants Pending");
            assertThat(adapter.runtimeSql())
                    .contains("sys.dm_os_schedulers", "sys.dm_exec_requests");
            assertThat(adapter.waitStatsSql()).contains("sys.dm_os_wait_stats", "wait_category");
            assertThat(adapter.storageSql()).contains("sys.dm_io_virtual_file_stats");
            if (!"2008R2".equals(version)) {
                assertThat(adapter.storageSql()).contains("sys.dm_db_log_space_usage");
            }
            assertThat(adapter.dmvTopSql()).contains("sys.dm_exec_query_stats");
            assertThat(adapter.deadlockEventsSql()).contains("system_health", "xml_deadlock_report");
            assertThat(adapter.blockingChainSql()).contains("blocking_session_id");
            assertThat(adapter.backupCoverageSql()).contains("msdb.dbo.backupset", "recovery_model_desc");
            assertThat(adapter.alwaysOnHealthSql()).contains("sys.dm_hadr_database_replica_states", "log_send_queue_size");
            assertThat(adapter.agentHealthSql()).contains("msdb.dbo.sysjobs").doesNotContain("command");
            assertThat(adapter.logShippingSql()).contains("log_shipping_monitor_primary");
            assertThat(adapter.replicationCdcSql()).contains("is_cdc_enabled");
            assertThat(adapter.configurationSnapshotSql()).contains("sys.configurations");
            assertThat(adapter.indexCandidatesSql()).contains("sys.dm_db_missing_index_group_stats");
            assertThat(String.join(" ", adapter.identitySql(), adapter.queryStoreCapabilitySql(),
                            adapter.performanceCountersSql(), adapter.runtimeSql(),
                            adapter.waitStatsSql(), adapter.storageSql(), adapter.queryStoreTopSql(),
                            adapter.dmvTopSql(), adapter.deadlockEventsSql(), adapter.blockingChainSql(),
                            adapter.backupCoverageSql(), adapter.alwaysOnHealthSql(), adapter.agentHealthSql(),
                            adapter.logShippingSql(), adapter.replicationCdcSql(), adapter.configurationSnapshotSql(),
                            adapter.indexCandidatesSql()))
                    .doesNotContain("ALTER ", "EXEC ", "UPDATE ", "DELETE ", "INSERT ");
        }
    }
}
