package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.SlowSqlSamplePoint;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 采集项：MySQL 5.6 慢查询日志表样本（差距分析 模块2）。
 *
 * <p>5.6 的 performance_schema 语句摘要能力不足（无 events_statements_history 默认可用配置），
 * {@link SlowSqlSampleItem} 在 5.6 上整体跳过。本项作为 5.6 的替代路径：
 * 当目标库开启 {@code slow_query_log=ON} 且 {@code log_output} 含 TABLE 时，
 * 增量读取 {@code mysql.slow_log} 表，映射为慢 SQL 样本落 metric_slow_sql_sample，
 * 补齐 5.6 实例的慢 SQL 列表能力（Top SQL 指纹聚合仍不支持，由查询侧降级提示）。
 *
 * <p>与 P_S 路径差异：
 * <ul>
 *   <li>无 DIGEST 指纹（digest 置空，不参与 Top SQL 聚合）；</li>
 *   <li>query_time/lock_time 为 TIME 列（整秒精度），亚秒级慢查询耗时取整；</li>
 *   <li>eventId 以 start_time 的 epoch 微秒合成（线程内单调递增，满足查询侧 (thread_id,event_id) 去重）。</li>
 * </ul>
 *
 * <p>水位：按实例记录已读取的最大 start_time，只拉取新增行；节点重启后水位清空，
 * 首轮回看窗口限制 10 分钟，配合查询侧去重兜底，避免重复灌入历史日志。
 * 未开启慢日志表输出时静默跳过（能力检测接口会向用户提示开启方法）。
 */
@Component
public class SlowLogTableSampleItem implements MySqlMetricItem {

    public static final String CODE = "slow_log_table_sample";

    /** 单轮样本上限。 */
    private static final int MAX_ROWS_PER_ROUND = 100;

    private static final int MAX_SQL_LEN = 65536;

    /** 节点重启（水位为空）时的首轮回看窗口。 */
    private static final long INITIAL_LOOKBACK_MS = 10 * 60_000L;

    /** instanceId -> 已读取的最大 start_time（epoch millis）。 */
    private final Map<Long, Long> watermarks = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        // 仅 5.6（无 P_S 能力）走慢日志表路径，5.7/8.0 由 SlowSqlSampleItem 采集
        if (adapter.supportsPerformanceSchema()) {
            return;
        }
        if (!isSlowLogTableEnabled(conn)) {
            return;
        }
        long instanceId = request.getInstanceId();
        long watermark = watermarks.getOrDefault(instanceId, System.currentTimeMillis() - INITIAL_LOOKBACK_MS);

        String sql = "SELECT start_time, user_host, "
                + "TIME_TO_SEC(query_time) AS query_sec, TIME_TO_SEC(lock_time) AS lock_sec, "
                + "rows_sent, rows_examined, db, thread_id, CONVERT(sql_text USING utf8) AS sql_text "
                + "FROM mysql.slow_log "
                + "WHERE start_time > ? "
                + "ORDER BY start_time ASC "
                + "LIMIT " + MAX_ROWS_PER_ROUND;

        long maxSeen = watermark;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            ps.setTimestamp(1, new Timestamp(watermark));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp startTime = rs.getTimestamp("start_time");
                    if (startTime == null) {
                        continue;
                    }
                    long startMs = startTime.getTime();
                    maxSeen = Math.max(maxSeen, startMs);
                    String sqlText = rs.getString("sql_text");
                    if (sqlText == null || sqlText.isBlank()) {
                        continue;
                    }
                    if (sqlText.length() > MAX_SQL_LEN) {
                        sqlText = sqlText.substring(0, MAX_SQL_LEN);
                    }
                    String userHost = rs.getString("user_host");
                    long threadId = rs.getLong("thread_id");
                    sink.addSlowSqlSample(new SlowSqlSamplePoint(
                            threadId,
                            // 无 P_S EVENT_ID：以 start_time epoch 微秒合成（线程内单调递增，去重键稳定）
                            startMs * 1000L,
                            parseUser(userHost),
                            parseHost(userHost),
                            rs.getString("db"),
                            null,
                            sqlText,
                            rs.getLong("query_sec") * 1_000_000L,
                            rs.getLong("lock_sec") * 1_000_000L,
                            rs.getLong("rows_examined"),
                            rs.getLong("rows_sent"),
                            0L,
                            false,
                            0L,
                            0L,
                            startMs));
                }
            }
        }
        watermarks.put(instanceId, maxSeen);
    }

    /** slow_query_log=ON 且 log_output 含 TABLE 时慢日志表才有增量数据。 */
    private static boolean isSlowLogTableEnabled(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SELECT @@GLOBAL.slow_query_log, @@GLOBAL.log_output")) {
                if (!rs.next()) {
                    return false;
                }
                boolean enabled = rs.getInt(1) == 1;
                String output = rs.getString(2);
                return enabled && output != null && output.toUpperCase().contains("TABLE");
            }
        }
    }

    /** user_host 形如 "app_user[app_user] @ web01 [10.0.0.8]"，取账号部分。 */
    static String parseUser(String userHost) {
        if (userHost == null) {
            return null;
        }
        int idx = userHost.indexOf('[');
        if (idx > 0) {
            return userHost.substring(0, idx).trim();
        }
        int at = userHost.indexOf('@');
        return at > 0 ? userHost.substring(0, at).trim() : userHost.trim();
    }

    /** user_host 中 @ 之后的主机部分（优先取中括号内 IP）。 */
    static String parseHost(String userHost) {
        if (userHost == null) {
            return null;
        }
        int at = userHost.indexOf('@');
        if (at < 0) {
            return null;
        }
        String hostPart = userHost.substring(at + 1).trim();
        int lb = hostPart.lastIndexOf('[');
        int rb = hostPart.lastIndexOf(']');
        if (lb >= 0 && rb > lb + 1) {
            return hostPart.substring(lb + 1, rb).trim();
        }
        return hostPart.isEmpty() ? null : hostPart;
    }

    /** 实例删除/暂停时清理水位。 */
    public void evict(long instanceId) {
        watermarks.remove(instanceId);
    }
}
