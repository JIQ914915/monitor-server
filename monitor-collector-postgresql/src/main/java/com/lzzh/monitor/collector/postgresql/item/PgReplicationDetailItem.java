package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：逐从库复制细分（二期 C8，分钟级，主库执行）。
 *
 * <p>{@code pg_stat_replication} 每行一个下游从库，按对象级指标写入
 * （object_type=replica，object_name=application_name@client_addr）：
 * <ul>
 *   <li>{@code pgrepl.lag_bytes} —— 主库当前 WAL 位点与从库回放位点的字节差（总落后量）；</li>
 *   <li>{@code pgrepl.write_lag_ms} / {@code pgrepl.flush_lag_ms} / {@code pgrepl.replay_lag_ms}
 *       —— 三段延迟（网络传输/从库刷盘/从库回放，毫秒）。三段对比可定位延迟卡在哪一环：
 *       write 高=网络慢，flush 高=从库磁盘慢，replay 高=从库回放跟不上（单进程回放/锁冲突）。
 *       从库空闲时三段延迟为 NULL（跳过不写），lag_bytes 仍有效。</li>
 * </ul>
 * 从库（恢复模式）上该视图无下游行，自然无输出；聚合指标（从库数/回放延迟）由 PgReplicationItem 负责。
 */
@Component
public class PgReplicationDetailItem implements PgMetricItem {

    public static final String CODE = "pg_repl_detail";

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
            try (ResultSet rs = st.executeQuery("""
                    SELECT COALESCE(NULLIF(application_name, ''), 'walreceiver')
                               || '@' || COALESCE(client_addr::text, 'local')       AS replica_name,
                           pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn)        AS lag_bytes,
                           EXTRACT(EPOCH FROM write_lag)  * 1000                    AS write_lag_ms,
                           EXTRACT(EPOCH FROM flush_lag)  * 1000                    AS flush_lag_ms,
                           EXTRACT(EPOCH FROM replay_lag) * 1000                    AS replay_lag_ms
                      FROM pg_stat_replication
                     WHERE state = 'streaming' OR state = 'catchup'
                    """)) {
                while (rs.next()) {
                    String name = rs.getString("replica_name");
                    double lagBytes = rs.getDouble("lag_bytes");
                    if (!rs.wasNull()) {
                        sink.addObject("pgrepl.lag_bytes", "replica", name, Math.max(0, lagBytes), ts);
                    }
                    addLag(sink, rs, "write_lag_ms", "pgrepl.write_lag_ms", name, ts);
                    addLag(sink, rs, "flush_lag_ms", "pgrepl.flush_lag_ms", name, ts);
                    addLag(sink, rs, "replay_lag_ms", "pgrepl.replay_lag_ms", name, ts);
                }
            }
        }
    }

    private static void addLag(PgMetricSink sink, ResultSet rs, String column, String metric,
                               String replicaName, long ts) throws SQLException {
        double v = rs.getDouble(column);
        if (!rs.wasNull()) {
            sink.addObject(metric, "replica", replicaName, Math.max(0, Math.round(v * 100) / 100.0), ts);
        }
    }
}
