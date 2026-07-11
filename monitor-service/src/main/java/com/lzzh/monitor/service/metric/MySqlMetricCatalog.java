package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.enums.MetricRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MySqlMetricCatalog implements DatabaseMetricCatalog {
    @Override public DbType supportedType() { return DbType.MYSQL; }
    @Override public String codeOf(MetricRole role) {
        if (role == MetricRole.UNUSED_INDEX_LIST) return "mysql.index.unused_list";
        throw new UnsupportedOperationException("MySQL 不支持指标语义: " + role);
    }
    @Override public List<String> numericParameterCodes() { return List.of("mysql.var.max_connections", "mysql.var.innodb_buffer_pool_size", "mysql.var.innodb_log_file_size", "mysql.var.innodb_log_files_in_group", "mysql.var.max_allowed_packet", "mysql.var.max_binlog_size", "mysql.var.table_open_cache", "mysql.var.thread_cache_size", "mysql.var.open_files_limit", "mysql.var.wait_timeout", "mysql.var.long_query_time", "mysql.var.tmp_table_size", "mysql.var.query_cache_size"); }
    @Override public List<String> textParameterCodes() { return List.of("mysql.var_text.sql_mode", "mysql.var_text.version", "mysql.var_text.time_zone", "mysql.var_text.character_set_server", "mysql.var_text.innodb_flush_log_at_trx_commit", "mysql.var_text.sync_binlog", "mysql.var_text.log_bin", "mysql.var_text.binlog_format", "mysql.var_text.gtid_mode", "mysql.var_text.enforce_gtid_consistency", "mysql.var_text.slow_query_log", "mysql.var_text.log_error", "mysql.var_text.general_log"); }
    @Override public String numericParameterPrefix() { return "mysql.var."; }
    @Override public String textParameterPrefix() { return "mysql.var_text."; }
}