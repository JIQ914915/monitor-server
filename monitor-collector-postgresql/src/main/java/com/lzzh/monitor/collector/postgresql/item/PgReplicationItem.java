package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：复制角色与延迟（分钟级，流复制）。
 * <ul>
 *   <li>pg.repl.is_replica：1=从库（恢复模式）/ 0=主库；</li>
 *   <li>从库：pg.repl.lag_seconds = now() - 最后回放事务时间（写入停止时该值会自然增长，
 *       与 MySQL Seconds_Behind_Master 语义差异已在指标说明中标注）；</li>
 *   <li>主库：pg.repl.replica_count = pg_stat_replication 下游从库数
 *       （从库掉线数量减少，可配"从库数量下降"规则）。</li>
 * </ul>
 */
@Component
public class PgReplicationItem implements PgMetricItem {

    public static final String CODE = "replication";

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
            try (ResultSet rs = st.executeQuery(adapter.replicationSql())) {
                if (!rs.next()) {
                    return;
                }
                boolean isReplica = rs.getBoolean("is_replica");
                sink.addNumeric("pg.repl.is_replica", isReplica ? 1.0 : 0.0, ts);
                if (isReplica) {
                    double lag = rs.getDouble("lag_seconds");
                    if (!rs.wasNull()) {
                        sink.addNumeric("pg.repl.lag_seconds", Math.max(0, lag), ts);
                    }
                } else {
                    double replicas = rs.getDouble("replica_count");
                    if (!rs.wasNull()) {
                        sink.addNumeric("pg.repl.replica_count", replicas, ts);
                    }
                }
            }
        }
    }
}
