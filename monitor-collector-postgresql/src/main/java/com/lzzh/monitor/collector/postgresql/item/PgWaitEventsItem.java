package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集项：PG 等待事件采样（二期 C1，分钟级，对标 MySQL WaitEventsItem）。
 *
 * <p>PG 无全局等待事件累积计数（对应 P_S waits summary 的视图不存在），
 * 业界通行做法（pgwatch/pganalyze 同款）是对 {@code pg_stat_activity} 周期采样：
 * 统计当前时刻各会话正在经历的等待事件，按 wait_event_type 大类聚合计数——
 * 数值是"此刻有多少个后端在等"，趋势上反映等待压力的持续水平。
 * <ul>
 *   <li>{@code pg.waits.lock_count} —— 重量级锁（行/表锁，最需要关注）</li>
 *   <li>{@code pg.waits.lwlock_count} —— 轻量级锁（共享内存结构竞争）</li>
 *   <li>{@code pg.waits.io_count} —— 数据文件/WAL 读写等待</li>
 *   <li>{@code pg.waits.ipc_count} —— 进程间通信（并行查询/复制同步等）</li>
 *   <li>{@code pg.waits.client_count} —— 等客户端收发（网络慢/应用取数慢）</li>
 *   <li>{@code pg.waits.timeout_count} —— 定时等待</li>
 *   <li>{@code pg.waits.other_count} —— 其余（Extension/BufferPin/Activity 等）</li>
 * </ul>
 * 文本指标 {@code pg.waits.top_events}：Top 10 具体等待事件 JSON
 * {@code [{"event":"Lock:transactionid","count":3}]}，供页面表格下钻。
 */
@Component
public class PgWaitEventsItem implements PgMetricItem {

    public static final String CODE = "pg_wait_events";

    private static final int TOP_N = 10;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();

        // 只统计客户端后端；空 wait_event_type 表示在跑 CPU（不算等待）
        String sql = "SELECT wait_event_type, wait_event, count(*) AS cnt "
                + "FROM pg_stat_activity "
                + "WHERE backend_type = 'client backend' "
                + "  AND pid <> pg_backend_pid() "
                + "  AND wait_event_type IS NOT NULL "
                + "GROUP BY wait_event_type, wait_event";

        long lock = 0, lwlock = 0, io = 0, ipc = 0, client = 0, timeout = 0, other = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String type = rs.getString("wait_event_type");
                    String event = rs.getString("wait_event");
                    long cnt = rs.getLong("cnt");
                    switch (type == null ? "" : type) {
                        case "Lock" -> lock += cnt;
                        case "LWLock" -> lwlock += cnt;
                        case "IO" -> io += cnt;
                        case "IPC" -> ipc += cnt;
                        case "Client" -> client += cnt;
                        case "Timeout" -> timeout += cnt;
                        default -> other += cnt;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("event", (type == null ? "?" : type) + ":" + (event == null ? "?" : event));
                    row.put("count", cnt);
                    details.add(row);
                }
            }
        }

        sink.addNumeric("pg.waits.lock_count", lock, ts);
        sink.addNumeric("pg.waits.lwlock_count", lwlock, ts);
        sink.addNumeric("pg.waits.io_count", io, ts);
        sink.addNumeric("pg.waits.ipc_count", ipc, ts);
        sink.addNumeric("pg.waits.client_count", client, ts);
        sink.addNumeric("pg.waits.timeout_count", timeout, ts);
        sink.addNumeric("pg.waits.other_count", other, ts);

        details.sort(Comparator.comparingLong(m -> -((Number) m.get("count")).longValue()));
        List<Map<String, Object>> top = details.size() > TOP_N ? details.subList(0, TOP_N) : details;
        sink.addText("pg.waits.top_events", toJson(top), ts);
    }

    /** 手写 JSON 序列化（本模块无 JSON 库依赖；事件名仅含字母/冒号，仍防御性转义）。 */
    private static String toJson(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"event\":\"").append(escape(String.valueOf(r.get("event"))))
                    .append("\",\"count\":").append(r.get("count"))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
