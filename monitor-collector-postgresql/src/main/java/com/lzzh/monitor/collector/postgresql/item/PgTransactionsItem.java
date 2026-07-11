package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：事务时长（分钟级）。
 * <ul>
 *   <li>pg.trx.max_seconds：最长运行事务的持续秒数（长事务阻碍 vacuum、引发膨胀的核心信号）；</li>
 *   <li>pg.trx.active：进行中的事务数；</li>
 *   <li>pg.trx.idle_in_trx_max_seconds：最长"事务中空闲"持续秒数（应用忘提交的典型形态）。</li>
 * </ul>
 */
@Component
public class PgTransactionsItem implements PgMetricItem {

    public static final String CODE = "transactions";

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
            try (ResultSet rs = st.executeQuery(adapter.transactionsSql())) {
                if (rs.next()) {
                    sink.addNumeric("pg.trx.max_seconds", rs.getDouble("max_trx_seconds"), ts);
                    sink.addNumeric("pg.trx.active", rs.getDouble("active_trx"), ts);
                    sink.addNumeric("pg.trx.idle_in_trx_max_seconds",
                            rs.getDouble("idle_in_trx_max_seconds"), ts);
                }
            }
        }
    }
}
