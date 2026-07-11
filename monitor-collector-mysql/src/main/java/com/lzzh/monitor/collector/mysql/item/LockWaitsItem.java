package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * 采集项：InnoDB 锁等待（§P1-5）。
 *
 * <p>通过 {@link MySqlVersionAdapter#lockWaitsSql()} 获取版本差异 SQL：
 * <ul>
 *   <li>5.6：{@code information_schema.innodb_lock_waits}（requesting_trx_id 为被阻塞方）</li>
 *   <li>5.7：{@code sys.innodb_lock_waits}（waiting_trx_id 为被阻塞方）</li>
 *   <li>8.0：{@code performance_schema.data_lock_waits}（requesting_engine_transaction_id 为被阻塞方）</li>
 * </ul>
 *
 * <p>产出两条分钟级 gauge 指标：
 * <ul>
 *   <li>{@code mysql.innodb.lock_waits}：当前锁等待关系总数（总行数）</li>
 *   <li>{@code mysql.innodb.blocked_sessions}：被阻塞的唯一事务数</li>
 * </ul>
 */
@Component
public class LockWaitsItem implements MySqlMetricItem {

    private static final String CODE = "lock_waits";
    private static final int QUERY_TIMEOUT_SECONDS = 5;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request,
                        MySqlVersionAdapter adapter, MetricSink sink) throws SQLException {
        String sql = adapter.lockWaitsSql();
        if (sql == null || sql.isBlank()) {
            return;
        }
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                int totalPairs = 0;
                Set<String> blockedTxns = new HashSet<>();
                while (rs.next()) {
                    totalPairs++;
                    // 第一列约定为被阻塞方的事务 ID（各版本 SQL 的首列）
                    String waitingId = rs.getString(1);
                    if (waitingId != null && !waitingId.isBlank()) {
                        blockedTxns.add(waitingId);
                    }
                }
                sink.addNumeric("mysql.innodb.lock_waits", totalPairs, ts);
                sink.addNumeric("mysql.innodb.blocked_sessions", blockedTxns.size(), ts);
            }
        }
    }
}
