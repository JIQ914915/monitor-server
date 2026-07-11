package com.lzzh.monitor.collector.mysql.version;

/** MySQL 5.6 适配：performance_schema 能力有限，慢查询走慢日志表/降级方案。 */
public class MySql56Adapter implements MySqlVersionAdapter {

    @Override
    public String version() {
        return "5.6";
    }

    @Override
    public String lockWaitsSql() {
        // 5.6 无 sys/data_lock_waits，使用 information_schema.innodb_lock_waits 降级
        return "SELECT requesting_trx_id, blocking_trx_id "
                + "FROM information_schema.innodb_lock_waits";
    }

    @Override
    public String replicaStatusSql() {
        return "SHOW SLAVE STATUS";
    }

    @Override
    public boolean supportsPerformanceSchema() {
        return false;
    }
}
