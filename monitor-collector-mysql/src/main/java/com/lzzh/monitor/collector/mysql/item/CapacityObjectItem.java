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
 * 采集项：对象级容量明细（§7.3 容量域 / §21.2.5 对象级专用表，小时级）。
 * <p>逐表采集 data_length + index_length，落对象级专用表 metric_capacity_object，
 * objectType=table、objectName=库.表。用于容量 Top N、表增长趋势下钻。
 * 属较重采集项：排除系统库、取按体积排序的前 N 表以限流（§8.4）。
 */
@Component
public class CapacityObjectItem implements MySqlMetricItem {

    public static final String CODE = "capacity_object";

    /** 单实例采集的表数量上限（按体积倒序取 Top N）。 */
    private static final int TOP_N = 200;

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
        String sql = "SELECT table_schema, table_name, "
                + "COALESCE(data_length, 0) AS data_len, "
                + "COALESCE(index_length, 0) AS index_len, "
                + "COALESCE(table_rows, 0) AS row_cnt "
                + "FROM information_schema.tables "
                + "WHERE table_type = 'BASE TABLE' "
                + "AND table_schema NOT IN ('mysql', 'information_schema', 'performance_schema', 'sys') "
                + "ORDER BY (COALESCE(data_length, 0) + COALESCE(index_length, 0)) DESC "
                + "LIMIT " + TOP_N;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String objectName = rs.getString("table_schema") + "." + rs.getString("table_name");
                    double dataLen = rs.getDouble("data_len");
                    double indexLen = rs.getDouble("index_len");
                    sink.addObject("capacity.data_size_bytes", "table", objectName, dataLen, ts);
                    sink.addObject("capacity.index_size_bytes", "table", objectName, indexLen, ts);
                    sink.addObject("capacity.total_size_bytes", "table", objectName, dataLen + indexLen, ts);
                    sink.addObject("capacity.table_rows", "table", objectName, rs.getDouble("row_cnt"), ts);
                }
            }
        }
    }
}
