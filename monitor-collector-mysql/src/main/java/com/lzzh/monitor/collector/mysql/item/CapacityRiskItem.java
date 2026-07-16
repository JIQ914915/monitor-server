package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.common.enums.CollectFrequency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;

/** 天级 AUTO_INCREMENT 耗尽风险采集，全部为只读查询。 */
@Component
public class CapacityRiskItem implements MySqlMetricItem {
    public static final String CODE = "capacity_risk";
    private static final int MAX_AUTO_INCREMENT_TABLES = 1000;

    @Override public String code() { return CODE; }
    @Override public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.DAILY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink) {
        collectAutoIncrement(conn, sink);
    }

    private void collectAutoIncrement(Connection conn, MetricSink sink) {
        long ts = System.currentTimeMillis(); double maxUsage = 0;
        String sql = "SELECT t.table_schema,t.table_name,t.auto_increment,c.column_type "
                + "FROM information_schema.tables t JOIN information_schema.columns c "
                + "ON c.table_schema=t.table_schema AND c.table_name=t.table_name "
                + "AND lower(c.extra) LIKE '%auto_increment%' "
                + "WHERE t.auto_increment IS NOT NULL AND t.table_schema NOT IN "
                + "('mysql','information_schema','performance_schema','sys') "
                + "ORDER BY t.auto_increment DESC LIMIT " + MAX_AUTO_INCREMENT_TABLES;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(20); st.setMaxRows(MAX_AUTO_INCREMENT_TABLES);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    BigDecimal current = rs.getBigDecimal("auto_increment");
                    BigDecimal max = integerMax(rs.getString("column_type"));
                    if (current == null || max == null || max.signum() <= 0) continue;
                    double usage = current.multiply(BigDecimal.valueOf(100)).divide(max, 6, java.math.RoundingMode.HALF_UP).doubleValue();
                    String name = rs.getString("table_schema") + "." + rs.getString("table_name");
                    sink.addObject("mysql.capacity.auto_increment_usage_pct", "table", name, usage, ts);
                    maxUsage = Math.max(maxUsage, usage);
                }
            }
            sink.addNumeric("mysql.capacity.auto_increment_max_usage_pct", maxUsage, ts);
        } catch (SQLException e) {
            sink.addItemError(CODE + ".auto_increment", friendly(e, "无法读取业务表 AUTO_INCREMENT 信息，请确认账号可查看 information_schema"));
        }
    }

    static BigDecimal integerMax(String columnType) {
        if (columnType == null) return null;
        String type = columnType.toLowerCase(Locale.ROOT); boolean unsigned = type.contains("unsigned");
        if (type.startsWith("tinyint")) return BigDecimal.valueOf(unsigned ? 255L : 127L);
        if (type.startsWith("smallint")) return BigDecimal.valueOf(unsigned ? 65535L : 32767L);
        if (type.startsWith("mediumint")) return BigDecimal.valueOf(unsigned ? 16777215L : 8388607L);
        if (type.startsWith("int") || type.startsWith("integer")) return new BigDecimal(unsigned ? "4294967295" : "2147483647");
        if (type.startsWith("bigint")) return new BigDecimal(unsigned ? "18446744073709551615" : "9223372036854775807");
        return null;
    }

    private static String friendly(SQLException e, String fallback) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("denied") ? "采集失败：账号缺少权限；" + fallback : fallback;
    }
}
