package com.lzzh.monitor.collector.mysql.version;

/** MySQL 8.0 适配：performance_schema.data_lock_waits 等新对象。 */
public class MySql80Adapter implements MySqlVersionAdapter {

    @Override
    public String version() {
        return "8.0";
    }

    @Override
    public String lockWaitsSql() {
        return "SELECT requesting_engine_transaction_id, blocking_engine_transaction_id "
                + "FROM performance_schema.data_lock_waits";
    }

    @Override
    public String replicaStatusSql() {
        return "SHOW REPLICA STATUS";
    }

    @Override
    public boolean supportsPerformanceSchema() {
        return true;
    }

    @Override
    public boolean hasErrorLogTable() {
        return true;
    }

    @Override
    public boolean supportsGroupReplication() {
        return true;
    }
}
