package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：InnoDB 活跃事务（§7.3 InnoDB 事务域，分钟级，采样值）。
 * <ul>
 *   <li>活跃事务数：information_schema.innodb_trx 行数；</li>
 *   <li>最长事务运行秒数：用于发现长事务 / purge 堆积风险。</li>
 * </ul>
 * 需要采集账号具备 PROCESS 权限（§8.3）；无权限时该项失败，由 collector 记录、不影响其它项。
 */
@Component
public class InnodbTrxItem implements MySqlMetricItem {

    public static final String CODE = "innodb_trx";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        String sql = "SELECT COUNT(*) AS trx_count, "
                + "COALESCE(MAX(TIMESTAMPDIFF(SECOND, trx_started, NOW())), 0) AS max_trx_sec "
                + "FROM information_schema.innodb_trx";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                if (rs.next()) {
                    sink.addNumeric("mysql.innodb.trx_active", rs.getDouble("trx_count"), ts);
                    sink.addNumeric("mysql.innodb.trx_max_seconds", rs.getDouble("max_trx_sec"), ts);
                }
            }
        }
    }
}
