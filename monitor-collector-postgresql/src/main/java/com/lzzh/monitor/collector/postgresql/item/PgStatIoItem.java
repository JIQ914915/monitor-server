package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.postgresql.PgCollectionStatusCodes;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import jakarta.annotation.Resource;
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

    @Resource
    private PgCounterDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        String sql = adapter.statIoSql();
        if (sql == null) {
            sink.markUnavailable(CODE, PgCollectionStatusCodes.UNSUPPORTED);
            return;
        }
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {

                if (rs.next()) {
                    Double resetAge = PgStatsResetSupport.ageSeconds(rs, "stats_reset", ts);
                    if (resetAge != null) {
                        sink.addNumeric("pg.stats.io_reset_age_seconds", resetAge, ts);
                    }
                    addRate(sink, instanceId, "pg.io.read_rate", rs.getLong("reads"), ts);
                    addRate(sink, instanceId, "pg.io.write_rate", rs.getLong("writes"), ts);
                    addRate(sink, instanceId, "pg.io.extend_rate", rs.getLong("extends"), ts);
                    addDelta(sink, instanceId, "pg.io.read_time_ms_delta",
                            Math.round(rs.getDouble("read_time_ms")), ts);
                    addDelta(sink, instanceId, "pg.io.write_time_ms_delta",
                            Math.round(rs.getDouble("write_time_ms")), ts);
                    addNullableRate(sink, instanceId, "pg.io.read_bytes_rate", rs, "read_bytes", ts);
                    addNullableRate(sink, instanceId, "pg.io.write_bytes_rate", rs, "write_bytes", ts);
                    addNullableRate(sink, instanceId, "pg.io.extend_bytes_rate", rs, "extend_bytes", ts);
                }
            }
        }
    }

    private void addNullableRate(PgMetricSink sink, long instanceId, String metric,
                                 ResultSet rs, String column, long ts) throws SQLException {
        long value = rs.getLong(column);
        if (!rs.wasNull()) {
            addRate(sink, instanceId, metric, value, ts);
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

}
