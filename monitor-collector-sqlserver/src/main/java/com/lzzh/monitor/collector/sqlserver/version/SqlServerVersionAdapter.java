package com.lzzh.monitor.collector.sqlserver.version;

import com.lzzh.monitor.collector.spi.version.VersionAdapter;

/** SQL Server 版本适配器：所有采集 SQL 必须从明确版本适配器取得。 */
public interface SqlServerVersionAdapter extends VersionAdapter {
    default boolean supportsQueryStore() { return true; }
    String identitySql();
    String queryStoreCapabilitySql();
    String performanceCountersSql();
    String runtimeSql();
    String waitStatsSql();
    String storageSql();
    String queryStoreTopSql();
    String dmvTopSql();
    String deadlockEventsSql();
    String blockingChainSql();
    String backupCoverageSql();
    String alwaysOnHealthSql();
    String agentHealthSql();
    String logShippingSql();
    String replicationCdcSql();
    String configurationSnapshotSql();
    String indexCandidatesSql();
}
