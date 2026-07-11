package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：容量（小时级）。
 * <ul>
 *   <li>pg.capacity.db_size_bytes：当前监控库大小；</li>
 *   <li>pg.capacity.total_size_bytes：实例内全部业务库总大小（排除模板库）。</li>
 * </ul>
 * pg_database_size 需要 CONNECT 权限；对无权限库会抛错，由 item 错误通道记录并降级。
 */
@Component
public class PgCapacityItem implements PgMetricItem {

    public static final String CODE = "capacity";

    /** 容量统计为重查询，放宽语句超时。 */
    private static final int CAPACITY_QUERY_TIMEOUT_SECONDS = 60;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(CAPACITY_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(adapter.capacitySql())) {
                if (rs.next()) {
                    sink.addNumeric("pg.capacity.db_size_bytes", rs.getDouble("db_size_bytes"), ts);
                    double total = rs.getDouble("total_size_bytes");
                    if (!rs.wasNull()) {
                        sink.addNumeric("pg.capacity.total_size_bytes", total, ts);
                    }
                }
            }
        }
    }
}
