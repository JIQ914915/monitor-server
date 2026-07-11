package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.enums.MetricRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PostgreSqlMetricCatalog implements DatabaseMetricCatalog {
    @Override public DbType supportedType() { return DbType.POSTGRESQL; }
    @Override public String codeOf(MetricRole role) {
        if (role == MetricRole.UNUSED_INDEX_LIST) return "pg.index.unused_list";
        throw new UnsupportedOperationException("PostgreSQL 不支持指标语义: " + role);
    }
    @Override public List<String> numericParameterCodes() { return List.of("pg.setting.max_connections", "pg.setting.shared_buffers_bytes", "pg.setting.effective_cache_size_bytes", "pg.setting.work_mem_bytes", "pg.setting.maintenance_work_mem_bytes", "pg.setting.max_wal_size_bytes", "pg.setting.checkpoint_timeout_seconds", "pg.setting.autovacuum_max_workers", "pg.setting.max_worker_processes", "pg.setting.idle_in_trx_timeout_ms", "pg.setting.statement_timeout_ms"); }
    @Override public List<String> textParameterCodes() { return List.of("pg.setting_text.server_version", "pg.setting_text.wal_level", "pg.setting_text.archive_mode", "pg.setting_text.hot_standby", "pg.setting_text.autovacuum", "pg.setting_text.ssl", "pg.setting_text.shared_preload_libraries", "pg.setting_text.log_min_duration_statement"); }
    @Override public String numericParameterPrefix() { return "pg.setting."; }
    @Override public String textParameterPrefix() { return "pg.setting_text."; }
}