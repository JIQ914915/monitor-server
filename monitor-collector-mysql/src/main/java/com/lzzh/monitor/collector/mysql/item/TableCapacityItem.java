package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：库表容量（§7.3 容量域，小时级，采样值）。
 * <p>汇总 information_schema.tables 的数据/索引大小，排除系统库；用于容量趋势与扩容规划。
 * 属较重查询，按小时级采集（§8.4）。表级明细（分对象）走对象级专用表，见 §21.2.5。
 * <p>需要采集账号对业务库具备 SELECT（information_schema.tables 仅返回有权限对象，§8.3）。
 */
@Component
public class TableCapacityItem implements MySqlMetricItem {

    public static final String CODE = "table_capacity";

    /** 查询级超时（秒）：information_schema.tables 属重查询，超时由数据库侧 KILL QUERY 处理，避免 socketTimeout 关闭连接。 */
    private static final int QUERY_TIMEOUT_SECONDS = 20;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        String sql = "SELECT "
                + "COALESCE(SUM(data_length), 0) AS data_len, "
                + "COALESCE(SUM(index_length), 0) AS index_len, "
                + "COUNT(*) AS table_count "
                + "FROM information_schema.tables "
                + "WHERE table_schema NOT IN ('mysql', 'information_schema', 'performance_schema', 'sys')";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    double dataLen = rs.getDouble("data_len");
                    double indexLen = rs.getDouble("index_len");
                    sink.addNumeric("mysql.capacity.data_size_bytes", dataLen, ts);
                    sink.addNumeric("mysql.capacity.index_size_bytes", indexLen, ts);
                    sink.addNumeric("mysql.capacity.total_size_bytes", dataLen + indexLen, ts);
                    sink.addNumeric("mysql.capacity.table_count", rs.getDouble("table_count"), ts);
                }
            }
        }
    }
}
