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
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：错误日志分析（§P2-2，小时级）。
 *
 * <p>三版本差异化采集：
 * <ul>
 *   <li><b>8.0</b>（{@code hasErrorLogTable()} = true）：直接查询
 *       {@code performance_schema.error_log}，统计最近 1 小时内 Error / Warning
 *       级别事件数，并抓取最新一条 Error 文本。
 *       产出：{@code mysql.errorlog.error_count}、{@code mysql.errorlog.warning_count}（数值）
 *       + {@code mysql.errorlog.latest_error}（文本覆盖变更）。</li>
 *
 *   <li><b>5.7</b>（{@code supportsPerformanceSchema()} = true，无 error_log 表）：
 *       查询 {@code performance_schema.events_errors_summary_global_by_error}（MySQL 5.7.24+），
 *       汇总所有 {@code SUM_ERROR_RAISED >= 1000}（服务端错误号）的累积总量，
 *       使用节点内差值计算本轮增量。
 *       产出：{@code mysql.errorlog.error_count}（本轮增量）。</li>
 *
 *   <li><b>5.6</b>（{@code supportsPerformanceSchema()} = false）：错误日志仅落文件，
 *       无法通过 JDBC 读取，静默跳过不采集。</li>
 * </ul>
 */
@Component
public class ErrorLogItem implements MySqlMetricItem {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogItem.class);
    private static final String CODE = "error_log";
    private static final int QUERY_TIMEOUT_SECS = 10;

    /**
     * 节点内差值基线（instanceId → 上轮累计错误数）。
     * 用于 5.7 版本计算本轮增量；仅在节点内有效，重启后首轮无增量输出。
     */
    private final ConcurrentHashMap<Long, Long> prevErrorTotal = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        if (adapter.hasErrorLogTable()) {
            collect80(conn, request.getInstanceId(), sink);
        } else if (adapter.supportsPerformanceSchema()) {
            collect57(conn, request.getInstanceId(), sink);
        } else {
            log.debug("实例 {} MySQL 5.6 不支持通过 JDBC 读取错误日志，跳过", request.getInstanceId());
        }
    }

    // ---- 8.0：performance_schema.error_log ----

    private void collect80(Connection conn, Long instanceId, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();
        long errorCount = 0;
        long warningCount = 0;

        // 最近 1 小时 Error/Warning 数量（按 PRIO 分组）
        String countSql = "SELECT PRIO, COUNT(*) FROM performance_schema.error_log "
                + "WHERE LOGGED >= NOW() - INTERVAL 1 HOUR "
                + "AND PRIO IN ('Error', 'Warning') "
                + "GROUP BY PRIO";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECS);
            try (ResultSet rs = st.executeQuery(countSql)) {
                while (rs.next()) {
                    String prio = rs.getString(1);
                    long cnt = rs.getLong(2);
                    if ("Error".equalsIgnoreCase(prio)) {
                        errorCount = cnt;
                    } else if ("Warning".equalsIgnoreCase(prio)) {
                        warningCount = cnt;
                    }
                }
            }
        }
        sink.addNumeric("mysql.errorlog.error_count", errorCount, ts);
        sink.addNumeric("mysql.errorlog.warning_count", warningCount, ts);

        // 登录失败监控（§15.4.3）：最近 1 小时错误日志中的 Access denied 条数。
        // 注意：MySQL 默认 log_error_verbosity=2 时部分认证失败仅计入 Aborted_connects
        // 不落错误日志，此指标与 mysql.delta.aborted_connects 互补（后者全版本可用）
        String deniedSql = "SELECT COUNT(*) FROM performance_schema.error_log "
                + "WHERE LOGGED >= NOW() - INTERVAL 1 HOUR "
                + "AND DATA LIKE '%Access denied%'";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECS);
            try (ResultSet rs = st.executeQuery(deniedSql)) {
                if (rs.next()) {
                    sink.addNumeric("mysql.security.access_denied_count", rs.getLong(1), ts);
                }
            }
        }

        // 最新一条 Error 文本（覆盖变更存储）
        String latestSql = "SELECT DATA FROM performance_schema.error_log "
                + "WHERE PRIO = 'Error' ORDER BY LOGGED DESC LIMIT 1";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECS);
            try (ResultSet rs = st.executeQuery(latestSql)) {
                if (rs.next()) {
                    String data = rs.getString(1);
                    if (data != null && !data.isBlank()) {
                        sink.addText("mysql.errorlog.latest_error", data, ts);
                    }
                }
            }
        }
    }

    // ---- 5.7：events_errors_summary_global_by_error 差值 ----
    // 该表 MySQL 5.7.7+ 才有；更早版本或部分分支可能不存在，捕获后静默跳过

    private void collect57(Connection conn, Long instanceId, MetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();

        // 汇总服务端错误（ERROR_NUMBER >= 1000）的累积总量
        String sumSql = "SELECT IFNULL(SUM(SUM_ERROR_RAISED), 0) "
                + "FROM performance_schema.events_errors_summary_global_by_error "
                + "WHERE SUM_ERROR_RAISED > 0 AND ERROR_NUMBER >= 1000";
        long currentTotal;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECS);
            try (ResultSet rs = st.executeQuery(sumSql)) {
                currentTotal = rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            // error 1146 = Table doesn't exist，说明此 5.7 小版本（< 5.7.7）无该表，静默跳过
            if (e.getErrorCode() == 1146 || (e.getMessage() != null
                    && e.getMessage().contains("doesn't exist"))) {
                log.debug("实例 {} performance_schema.events_errors_summary_global_by_error 不存在"
                        + "（MySQL 5.7.7 以前版本），跳过错误日志采集", instanceId);
                return;
            }
            throw e;
        }

        Long prev = prevErrorTotal.get(instanceId);
        if (prev != null && currentTotal >= prev) {
            long delta = currentTotal - prev;
            sink.addNumeric("mysql.errorlog.error_count", delta, ts);
        }
        // 首轮（prev == null）或计数器重置（currentTotal < prev）时不产出，下轮开始正常
        prevErrorTotal.put(instanceId, currentTotal);
    }
}
