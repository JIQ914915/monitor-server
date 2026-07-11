package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.lzzh.monitor.common.enums.CollectFrequency;

/**
 * 采集项：关键参数快照（天级配置快照，对标 MySQL VariablesItem）。
 *
 * <p>数值型参数写 {@code pg.setting.<name>}（字节类参数统一换算为字节），
 * 文本型参数写 {@code pg.setting_text.<name>}。
 * {@code pg.setting.max_connections} 同时作为配置快照存在性的 marker 指标
 * （见 PostgreSqlCollector.configSnapshotMarkerMetric）。
 */
@Component
public class PgSettingsItem implements PgMetricItem {

    public static final String CODE = "settings";

    /** 数值型参数（单位归一化到字节/秒/个，unit 换算交给 pg_size_bytes 与 setting 单位）。 */
    private static final Map<String, String> NUMERIC_SETTINGS = new LinkedHashMap<>();
    /** 文本型参数：状态/枚举类，写文本指标供参数页与巡检展示。 */
    private static final String[] TEXT_SETTINGS = {
            "wal_level", "archive_mode", "hot_standby", "autovacuum",
            "ssl", "shared_preload_libraries", "server_version", "log_min_duration_statement"
    };

    static {
        NUMERIC_SETTINGS.put("max_connections", "pg.setting.max_connections");
        NUMERIC_SETTINGS.put("shared_buffers", "pg.setting.shared_buffers_bytes");
        NUMERIC_SETTINGS.put("effective_cache_size", "pg.setting.effective_cache_size_bytes");
        NUMERIC_SETTINGS.put("work_mem", "pg.setting.work_mem_bytes");
        NUMERIC_SETTINGS.put("maintenance_work_mem", "pg.setting.maintenance_work_mem_bytes");
        NUMERIC_SETTINGS.put("max_wal_size", "pg.setting.max_wal_size_bytes");
        NUMERIC_SETTINGS.put("checkpoint_timeout", "pg.setting.checkpoint_timeout_seconds");
        NUMERIC_SETTINGS.put("autovacuum_max_workers", "pg.setting.autovacuum_max_workers");
        NUMERIC_SETTINGS.put("max_worker_processes", "pg.setting.max_worker_processes");
        NUMERIC_SETTINGS.put("idle_in_transaction_session_timeout", "pg.setting.idle_in_trx_timeout_ms");
        NUMERIC_SETTINGS.put("statement_timeout", "pg.setting.statement_timeout_ms");
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        // pg_settings 一次取全量，内存过滤：避免 IN 列表拼接
        String sql = "SELECT name, setting, unit FROM pg_settings";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String setting = rs.getString("setting");
                    String unit = rs.getString("unit");
                    String metric = NUMERIC_SETTINGS.get(name);
                    if (metric != null) {
                        Double v = toCanonical(setting, unit);
                        if (v != null) {
                            sink.addNumeric(metric, v, ts);
                        }
                        continue;
                    }
                    for (String textName : TEXT_SETTINGS) {
                        if (textName.equals(name)) {
                            sink.addText("pg.setting_text." + name, setting == null ? "" : setting, ts);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * 把 pg_settings.setting 按 unit 归一化：
     * 内存类（8kB/kB/MB/GB）→ 字节；时间类（ms/s/min/h/d）→ 目标指标已按名字标注单位，
     * ms 保持毫秒、s/min 换算为秒。无单位按原值。非数值返回 null。
     */
    private static Double toCanonical(String setting, String unit) {
        double v;
        try {
            v = Double.parseDouble(setting);
        } catch (Exception e) {
            return null;
        }
        if (unit == null || unit.isBlank()) {
            return v;
        }
        return switch (unit) {
            case "B" -> v;
            case "kB" -> v * 1024;
            case "8kB" -> v * 8192;
            case "MB" -> v * 1024 * 1024;
            case "GB" -> v * 1024 * 1024 * 1024;
            case "ms" -> v;
            case "s" -> v;
            case "min" -> v * 60;
            case "h" -> v * 3600;
            case "d" -> v * 86400;
            default -> v;
        };
    }
}
