package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：等待事件分析（§8.1 / 15.3，分钟级，5.7/8.0）。
 *
 * <p>从 {@code performance_schema.events_waits_summary_global_by_event_name} 读取各等待事件的
 * 累积计数与累积耗时，节点内做差值得到"本采集周期"的等待增量，按事件名前缀聚合为大类：
 * <ul>
 *   <li>{@code mysql.waits.io_file_ms} —— 文件 I/O 等待（wait/io/file/*，数据/日志文件读写）；</li>
 *   <li>{@code mysql.waits.io_table_ms} —— 表 I/O 等待（wait/io/table/*，行读写入口）；</li>
 *   <li>{@code mysql.waits.lock_ms} —— 锁等待（wait/lock/*，表锁/元数据锁）；</li>
 *   <li>{@code mysql.waits.synch_ms} —— 同步原语等待（wait/synch/*，mutex/rwlock，默认关闭仪器时恒为 0）；</li>
 *   <li>{@code mysql.waits.other_ms} —— 其余（socket 等）。</li>
 * </ul>
 * 同时产出文本指标 {@code mysql.waits.top_events}：本周期耗时 Top 10 等待事件明细 JSON
 * {@code [{"event","count","timeMs","avgUs"}]}，供页面表格下钻"到底在等什么"。
 *
 * <p>说明：采集的是 P_S 已启用仪器的数据（5.7/8.0 默认启用 wait/io/file、wait/io/table、wait/lock/table 等），
 * 未启用的仪器不计入，本项不修改目标库 setup_instruments 配置。5.6 无稳定的 waits summary 能力，跳过。
 * 首轮无基线不产出，计数器重置（P_S TRUNCATE / 实例重启）时该轮跳过。
 */
@Component
public class WaitEventsItem implements MySqlMetricItem {

    public static final String CODE = "wait_events";

    /** 皮秒 → 毫秒。 */
    private static final double PS_TO_MS = 1_000_000_000.0;
    /** 皮秒 → 微秒。 */
    private static final double PS_TO_US = 1_000_000.0;

    private static final int TOP_N = 10;

    /** instanceId → (eventName → [countStar, sumTimerPs]) 上轮快照。 */
    private final Map<Long, Map<String, long[]>> baselines = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        if (!adapter.supportsPerformanceSchema()) {
            return;
        }
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();

        Map<String, long[]> current = new HashMap<>();
        String sql = "SELECT EVENT_NAME, COUNT_STAR, SUM_TIMER_WAIT "
                + "FROM performance_schema.events_waits_summary_global_by_event_name "
                + "WHERE COUNT_STAR > 0 AND EVENT_NAME <> 'idle'";
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String event = rs.getString(1);
                    if (event == null) {
                        continue;
                    }
                    current.put(event, new long[]{rs.getLong(2), rs.getLong(3)});
                }
            }
        }

        Map<String, long[]> prev = baselines.put(instanceId, current);
        if (prev == null) {
            // 首轮建基线不产出
            return;
        }

        // 逐事件差值 → 大类聚合 + Top N 明细
        double ioFileMs = 0, ioTableMs = 0, lockMs = 0, synchMs = 0, otherMs = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        for (Map.Entry<String, long[]> e : current.entrySet()) {
            long[] cur = e.getValue();
            long[] base = prev.get(e.getKey());
            long countDelta = base == null ? cur[0] : cur[0] - base[0];
            long timerDelta = base == null ? cur[1] : cur[1] - base[1];
            if (countDelta < 0 || timerDelta < 0) {
                // 计数器重置（TRUNCATE P_S / 实例重启）：本事件跳过，下轮恢复
                continue;
            }
            if (countDelta == 0 && timerDelta == 0) {
                continue;
            }
            double timeMs = timerDelta / PS_TO_MS;
            String name = e.getKey();
            if (name.startsWith("wait/io/file/")) {
                ioFileMs += timeMs;
            } else if (name.startsWith("wait/io/table/")) {
                ioTableMs += timeMs;
            } else if (name.startsWith("wait/lock/")) {
                lockMs += timeMs;
            } else if (name.startsWith("wait/synch/")) {
                synchMs += timeMs;
            } else {
                otherMs += timeMs;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("event", name);
            row.put("count", countDelta);
            row.put("timeMs", round2(timeMs));
            row.put("avgUs", countDelta > 0 ? round2(timerDelta / PS_TO_US / countDelta) : 0);
            details.add(row);
        }

        sink.addNumeric("mysql.waits.io_file_ms", round2(ioFileMs), ts);
        sink.addNumeric("mysql.waits.io_table_ms", round2(ioTableMs), ts);
        sink.addNumeric("mysql.waits.lock_ms", round2(lockMs), ts);
        sink.addNumeric("mysql.waits.synch_ms", round2(synchMs), ts);
        sink.addNumeric("mysql.waits.other_ms", round2(otherMs), ts);

        details.sort(Comparator.comparingDouble(m -> -((Number) m.get("timeMs")).doubleValue()));
        List<Map<String, Object>> top = details.size() > TOP_N ? details.subList(0, TOP_N) : details;
        sink.addText("mysql.waits.top_events", toJson(top), ts);
    }

    /** 手写 JSON 序列化（本模块无 JSON 库依赖；事件名仅含字母/斜杠，仍防御性转义）。 */
    private static String toJson(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"event\":\"").append(escape(String.valueOf(r.get("event"))))
                    .append("\",\"count\":").append(r.get("count"))
                    .append(",\"timeMs\":").append(r.get("timeMs"))
                    .append(",\"avgUs\":").append(r.get("avgUs"))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 实例删除/暂停时清理基线。 */
    public void evict(long instanceId) {
        baselines.remove(instanceId);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
