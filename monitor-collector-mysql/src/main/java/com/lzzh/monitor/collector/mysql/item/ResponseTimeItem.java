package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：语句平均响应时间（§7.3 性能域，分钟级）。
 * <p>数据来源：{@code performance_schema.events_statements_summary_global_by_event_name}，
 * 汇总所有 {@code statement/%} 事件的累积等待时间（{@code SUM_TIMER_WAIT}，皮秒）
 * 和执行次数（{@code COUNT_STAR}），通过 {@link CounterDeltaStore} 对两者分别求差值，
 * 再相除得到当前周期内语句平均延迟（毫秒），产出 {@code mysql.perf.avg_stmt_latency_ms}。
 *
 * <p><b>前提条件</b>：需要 performance_schema 已启用（MySQL 默认 5.6.6+ 开启，
 * 5.6 早期版本可能关闭）。若 performance_schema 未启用或查询失败，本采集项静默跳过，
 * 不影响其他采集项。
 *
 * <p><b>计算方法</b>：
 * <ol>
 *   <li>当前快照：{@code SUM(SUM_TIMER_WAIT)} / {@code SUM(COUNT_STAR)}</li>
 *   <li>本周期增量：{@code delta_timer_ps} / {@code delta_count}</li>
 *   <li>转换为毫秒：{@code delta_timer_ps / delta_count / 1_000_000_000}</li>
 * </ol>
 *
 * <p>皮秒（ps）→ 毫秒（ms）转换：除以 10^9。
 */
@Component
public class ResponseTimeItem implements MySqlMetricItem {

    private static final Logger log = LoggerFactory.getLogger(ResponseTimeItem.class);

    public static final String CODE = "response_time";

    /** 皮秒 → 毫秒转换系数（1ms = 10^9 ps）。 */
    private static final double PS_TO_MS = 1_000_000_000.0;

    /** 查询所有 statement 类型的累积等待时间和执行次数。 */
    private static final String QUERY_SQL =
            "SELECT SUM(SUM_TIMER_WAIT) AS total_timer, SUM(COUNT_STAR) AS total_count "
                    + "FROM performance_schema.events_statements_summary_global_by_event_name "
                    + "WHERE EVENT_NAME LIKE 'statement/%'";

    private final CounterDeltaStore deltaStore;

    public ResponseTimeItem(CounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        long instanceId = request.getInstanceId();

        long totalTimerPs;
        long totalCount;

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(QUERY_SQL)) {
                if (!rs.next()) {
                    return;
                }
                Object timerObj = rs.getObject("total_timer");
                Object countObj = rs.getObject("total_count");
                if (timerObj == null || countObj == null) {
                    return;
                }
                totalTimerPs = ((Number) timerObj).longValue();
                totalCount   = ((Number) countObj).longValue();
            }
        } catch (SQLException e) {
            // performance_schema 未启用时 TABLE_NOT_FOUND 或权限不足，静默跳过
            log.debug("[ResponseTimeItem] instance={} skip, reason={}", instanceId, e.getMessage());
            return;
        }

        // 对 SUM_TIMER_WAIT 累积值求差值（皮秒增量）
        Long deltaTimerPs = deltaStore.delta(instanceId, "rt.sum_timer_wait", totalTimerPs, ts);
        // 对 COUNT_STAR 累积值求差值（次数增量）
        Long deltaCount   = deltaStore.delta(instanceId, "rt.count_star",     totalCount,   ts);

        if (deltaTimerPs == null || deltaCount == null || deltaCount == 0) {
            // 首次采样、计数器回绕或本周期无新语句，跳过
            return;
        }

        // 周期内平均延迟（毫秒），保留 3 位小数
        double avgLatencyMs = (deltaTimerPs / (double) deltaCount) / PS_TO_MS;
        avgLatencyMs = Math.round(avgLatencyMs * 1000.0) / 1000.0;

        sink.addNumeric("mysql.perf.avg_stmt_latency_ms", avgLatencyMs, ts);
    }
}
