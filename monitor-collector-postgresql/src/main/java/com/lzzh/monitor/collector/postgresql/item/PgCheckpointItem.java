package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：检查点与 bgwriter（分钟级，pg_stat_bgwriter 差值）。
 * <ul>
 *   <li>pg.ckpt.timed_delta / pg.ckpt.req_delta：周期内定时/请求触发的检查点次数，
 *       req 占比持续偏高说明 max_wal_size 偏小，检查点风暴会造成 IO 抖动；</li>
 *   <li>pg.bgwriter.buffers_checkpoint_rate / buffers_clean_rate / buffers_backend_rate：
 *       三种途径的脏页刷盘速率（页/秒），backend 占比高说明后端进程被迫自己刷盘，共享缓冲区吃紧。</li>
 * </ul>
 * 版本说明：13–16 字段一致；PG 17 起检查点计数迁到 pg_stat_checkpointer，届时增加 Pg17Adapter 覆盖。
 */
@Component
public class PgCheckpointItem implements PgMetricItem {

    public static final String CODE = "pg_checkpoint";

    @Resource
    private PgCounterDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("""
                    SELECT checkpoints_timed, checkpoints_req,
                           buffers_checkpoint, buffers_clean, buffers_backend
                      FROM pg_stat_bgwriter
                    """)) {
                if (!rs.next()) {
                    return;
                }
                addDelta(sink, "pg.ckpt.timed_delta", instanceId, rs.getLong("checkpoints_timed"), ts);
                addDelta(sink, "pg.ckpt.req_delta", instanceId, rs.getLong("checkpoints_req"), ts);
                addRate(sink, "pg.bgwriter.buffers_checkpoint_rate", instanceId, rs.getLong("buffers_checkpoint"), ts);
                addRate(sink, "pg.bgwriter.buffers_clean_rate", instanceId, rs.getLong("buffers_clean"), ts);
                addRate(sink, "pg.bgwriter.buffers_backend_rate", instanceId, rs.getLong("buffers_backend"), ts);
            }
        }
    }

    private void addDelta(PgMetricSink sink, String metric, long instanceId, long value, long ts) {
        Long delta = deltaStore.delta(instanceId, metric, value, ts);
        if (delta != null) {
            sink.addNumeric(metric, delta, ts);
        }
    }

    private void addRate(PgMetricSink sink, String metric, long instanceId, long value, long ts) {
        Double rate = deltaStore.rate(instanceId, metric, value, ts);
        if (rate != null) {
            sink.addNumeric(metric, rate, ts);
        }
    }
}
