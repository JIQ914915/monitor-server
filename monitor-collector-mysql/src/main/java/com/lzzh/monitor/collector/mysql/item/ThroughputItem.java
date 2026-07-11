package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 采集项：吞吐率 QPS / TPS（§7.3 吞吐域，分钟级，差值加工）。
 * <p>QPS/TPS 源自 SHOW GLOBAL STATUS 累积计数器，按两次采样差值除以时间间隔求速率：
 * <ul>
 *   <li>QPS = Δ(Questions) / Δt(秒)</li>
 *   <li>TPS = Δ(Com_commit + Com_rollback) / Δt(秒)</li>
 * </ul>
 * 差值/回绕处理复用 {@link CounterDeltaStore}；首次采样或计数器回绕时用
 * "自实例启动以来均值"(counter / Uptime) 兜底，避免出现空点。
 */
@Component
public class ThroughputItem implements MySqlMetricItem {

    public static final String CODE = "throughput";

    private static final Set<String> WANTED = Set.of("Questions", "Com_commit", "Com_rollback", "Uptime");

    @Resource
    private CounterDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        long instanceId = request.getInstanceId();
        // 优先复用 GlobalStatusItem 缓存的全量快照，避免重复对目标库发起全量查询
        Map<String, Long> snapshot = sink.getGlobalStatusSnapshot();
        Map<String, Long> status = (snapshot != null) ? snapshot : readStatus(conn);
        long questions = status.getOrDefault("Questions", 0L);
        long transactions = status.getOrDefault("Com_commit", 0L) + status.getOrDefault("Com_rollback", 0L);
        long uptime = status.getOrDefault("Uptime", 0L);

        Double qps = deltaStore.rate(instanceId, "throughput.questions", questions, ts);
        Double tps = deltaStore.rate(instanceId, "throughput.transactions", transactions, ts);
        if (qps == null) {
            qps = uptime > 0 ? (double) questions / uptime : 0;
        }
        if (tps == null) {
            tps = uptime > 0 ? (double) transactions / uptime : 0;
        }

        sink.addNumeric("mysql.qps", round2(qps), ts);
        sink.addNumeric("mysql.tps", round2(tps), ts);
    }

    /** 一次查询 SHOW GLOBAL STATUS，抽取所需累计计数器。 */
    private Map<String, Long> readStatus(Connection conn) throws SQLException {
        Map<String, Long> map = new HashMap<>();
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW GLOBAL STATUS")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    if (!WANTED.contains(name)) {
                        continue;
                    }
                    try {
                        map.put(name, Long.parseLong(rs.getString(2).trim()));
                    } catch (NumberFormatException ignore) {
                        // 非数值项忽略
                    }
                }
            }
        }
        return map;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
