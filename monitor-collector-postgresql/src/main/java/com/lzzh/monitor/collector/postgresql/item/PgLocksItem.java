package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：锁等待（分钟级）。
 * <ul>
 *   <li>pg.locks.waiting：未授予的锁请求数（pg_locks WHERE NOT granted）；</li>
 *   <li>pg.blocked_sessions：因等锁被阻塞的客户端会话数（wait_event_type='Lock'）。</li>
 * </ul>
 */
@Component
public class PgLocksItem implements PgMetricItem {

    public static final String CODE = "locks";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.locksSql())) {
                if (rs.next()) {
                    sink.addNumeric("pg.locks.waiting", rs.getDouble("waiting_locks"), ts);
                    sink.addNumeric("pg.blocked_sessions", rs.getDouble("blocked_sessions"), ts);
                }
            }
        }
    }
}
