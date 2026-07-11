package com.lzzh.monitor.collector.mysql.version;

/** MySQL 5.7 适配：使用 sys schema（sys.innodb_lock_waits 等）。 */
public class MySql57Adapter implements MySqlVersionAdapter {

    @Override
    public String version() {
        return "5.7";
    }

    @Override
    public String lockWaitsSql() {
        return "SELECT waiting_trx_id, blocking_trx_id, locked_table "
                + "FROM sys.innodb_lock_waits";
    }

    @Override
    public String replicaStatusSql() {
        return "SHOW SLAVE STATUS";
    }

    @Override
    public boolean supportsPerformanceSchema() {
        return true;
    }
}
