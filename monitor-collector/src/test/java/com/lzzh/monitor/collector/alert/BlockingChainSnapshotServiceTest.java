package com.lzzh.monitor.collector.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingChainSnapshotServiceTest {

    @Test
    void buildsPostgreSqlBlockingSnapshotQuery() {
        String sql = BlockingChainSnapshotService.blockingChainSql("PostgreSQL", "17");

        assertThat(sql)
                .contains("pg_blocking_pids", "pg_stat_activity", "pg_locks")
                .contains("waiting_pid", "blocking_pid", "locked_table");
    }

    @Test
    void keepsLegacyMySqlVersionRouting() {
        assertThat(BlockingChainSnapshotService.blockingChainSql("5.6"))
                .contains("information_schema.innodb_lock_waits");
        assertThat(BlockingChainSnapshotService.blockingChainSql("8.0"))
                .contains("sys.innodb_lock_waits");
    }
    @Test
    void buildsMetadataLockSnapshotQueryFor57And84() {
        assertThat(BlockingChainSnapshotService.metadataLockSql())
                .contains("performance_schema.metadata_locks", "LOCK_STATUS='PENDING'", "blocking_pid");
        assertThat(BlockingChainSnapshotService.blockingChainSql("8.4"))
                .contains("sys.innodb_lock_waits");
    }
}