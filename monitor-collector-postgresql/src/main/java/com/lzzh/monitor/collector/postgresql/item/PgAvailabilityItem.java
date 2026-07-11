package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：实例可用性探测 + 运行时长（分钟级）。
 *
 * <p>连接建立成功则此 item 被执行，写入 {@code pg.availability = 1} 与
 * {@code pg.uptime}（postmaster 启动至今秒数，供"实例重启"类规则使用）。
 * 连接失败时本 item 不会执行，CollectRunner 捕获连接异常后补写 {@code pg.availability = 0}。
 */
@Component
public class PgAvailabilityItem implements PgMetricItem {

    private static final String CODE = "availability";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        PgVersionAdapter adapter, PgMetricSink sink) throws SQLException {
        long ts = System.currentTimeMillis();
        sink.addNumeric("pg.availability", 1.0, ts);
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(
                    "SELECT EXTRACT(EPOCH FROM (now() - pg_postmaster_start_time())) AS uptime")) {
                if (rs.next()) {
                    sink.addNumeric("pg.uptime", rs.getDouble("uptime"), ts);
                }
            }
        }
    }
}
