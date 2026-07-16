package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：关键配置参数（§7.3 配置域，天级）。
 * <p>采集 SHOW GLOBAL VARIABLES 中的关键参数：
 * <ul>
 *   <li>数值型关键参数：发数值指标 mysql.var.&lt;name&gt;，便于阈值规则；</li>
 *   <li>文本型关键参数（sql_mode / version / time_zone 等）：发文本指标
 *       mysql.var_text.&lt;name&gt; 走覆盖变更存储（§9.1，仅参数变更时落库），用于配置巡检与变更审计。</li>
 * </ul>
 */
@Component
public class VariablesItem implements MySqlMetricItem {

    private static final Logger log = LoggerFactory.getLogger(VariablesItem.class);
    public static final String CODE = "variables";

    /** 关注的数值型关键参数白名单。 */
    private static final Set<String> WANTED = Set.of(
            "max_connections",
            "innodb_buffer_pool_size",
            "innodb_log_file_size",
            "innodb_log_files_in_group",
            "max_allowed_packet",
            "max_binlog_size",
            "binlog_expire_logs_seconds",
            "expire_logs_days",
            "performance_schema_digests_size",
            "performance_schema_max_digest_length",
            "performance_schema_max_sql_text_length",
            "table_open_cache",
            "thread_cache_size",
            "open_files_limit",
            "wait_timeout",
            "long_query_time",
            "tmp_table_size",         // 内存临时表上限（超出则转磁盘临时表）
            "max_heap_table_size",    // MEMORY 表上限（与 tmp_table_size 取小者生效，调优建议用）
            "query_cache_size"        // 查询缓存大小（5.6/5.7 有；8.0 已移除，会返回空）
    );

    /** 关注的文本型关键参数白名单（覆盖变更存储）。 */
    private static final Set<String> WANTED_TEXT = Set.of(
            "sql_mode",
            "performance_schema",
            "query_cache_type",
            "version",
            "time_zone",
            "character_set_server",
            "innodb_flush_log_at_trx_commit",
            "sync_binlog",
            "log_bin",
            "binlog_format",
            "gtid_mode",
            "enforce_gtid_consistency",
            "slow_query_log",
            "log_error",              // 错误日志文件路径（供配置巡检）
            "general_log"             // 通用查询日志开关（ON/OFF，开启时性能影响大）
    );

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW GLOBAL VARIABLES")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (name == null) {
                        continue;
                    }
                    String lower = name.toLowerCase();
                    String rawValue = rs.getString(2);
                    if (WANTED.contains(lower)) {
                        Double v = parseDouble(rawValue);
                        if (v != null) {
                            sink.addNumeric("mysql.var." + lower, v, ts);
                        }
                    } else if (WANTED_TEXT.contains(lower)) {
                        sink.addText("mysql.var_text." + lower, rawValue == null ? "" : rawValue, ts);
                    }
                }
            }
        }
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
