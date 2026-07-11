package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：实例级 I/O 统计（二期 C9，分钟级，PG 16+ 的 pg_stat_io）。
 *
 * <p>{@code pg_stat_io} 是 PG 16 引入的统一 I/O 视图，按 backend_type × object × context
 * 细分。本项聚焦实例级汇总（object='relation'，即业务表/索引 I/O），差值换算速率：
 * <ul>
 *   <li>{@code pg.io.read_rate} / {@code pg.io.write_rate} / {@code pg.io.extend_rate}
 *       —— 每秒块读 / 块写 / 文件扩展操作数；</li>
 *   <li>{@code pg.io.read_time_ms_delta} / {@code pg.io.write_time_ms_delta}
 *       —— 周期内块读/块写耗时（毫秒，需 track_io_timing=on，未开启时恒为 0）。</li>
 * </ul>
 * PG 13~15 无该视图，直接跳过（缓存命中率与 bgwriter 指标已覆盖基础 I/O 观测）。
 * 首轮建基线不产出。
 */
@Component
public class PgStatIoItem implements PgMetricItem {

    public static final String CODE = "pg_stat_io";

    private final PgCounterDeltaStore deltaStore;

    public PgStatIoItem(PgCounterDeltaStore deltaStore) {
        this.deltaStore = deltaStore;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        if (majorVersion(request.getVersion()) < 16) {
            return;
        }
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT COALESCE(SUM(reads), 0)      AS reads,
                           COALESCE(SUM(writes), 0)     AS writes,
                           COALESCE(SUM(extends), 0)    AS extends,
                           COALESCE(SUM(read_time), 0)  AS read_time_ms,
                           COALESCE(SUM(write_time), 0) AS write_time_ms
                      FROM pg_stat_io
                     WHERE object = 'relation'
                    """)) {
                if (rs.next()) {
                    addRate(sink, instanceId, "pg.io.read_rate", rs.getLong("reads"), ts);
                    addRate(sink, instanceId, "pg.io.write_rate", rs.getLong("writes"), ts);
                    addRate(sink, instanceId, "pg.io.extend_rate", rs.getLong("extends"), ts);
                    addDelta(sink, instanceId, "pg.io.read_time_ms_delta",
                            Math.round(rs.getDouble("read_time_ms")), ts);
                    addDelta(sink, instanceId, "pg.io.write_time_ms_delta",
                            Math.round(rs.getDouble("write_time_ms")), ts);
                }
            }
        }
    }

    private void addRate(PgMetricSink sink, long instanceId, String metric, long value, long ts) {
        Double rate = deltaStore.rate(instanceId, metric, value, ts);
        if (rate != null) {
            sink.addNumeric(metric, rate, ts);
        }
    }

    private void addDelta(PgMetricSink sink, long instanceId, String metric, long value, long ts) {
        Long delta = deltaStore.delta(instanceId, metric, value, ts);
        if (delta != null) {
            sink.addNumeric(metric, delta, ts);
        }
    }

    private static int majorVersion(String version) {
        if (version == null || version.isBlank()) {
            return 13;
        }
        try {
            String head = version.split("[^0-9]")[0];
            return head.isEmpty() ? 13 : Integer.parseInt(head);
        } catch (NumberFormatException e) {
            return 13;
        }
    }
}
