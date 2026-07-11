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

/**
 * 采集项：慢 SQL 真实执行样本（§7.3 SQL 域，分钟级）。
 *
 * <p>从 performance_schema.events_statements_history（5.7/8.0 默认开启，每线程保留最近 10 条）
 * 抓取 TIMER_WAIT >= long_query_time 的语句，保留<b>真实 SQL 文本（含参数）</b>，
 * 落 metric_slow_sql_sample 表供慢SQL列表展示。SQL_TEXT 长度受目标库
 * performance_schema_max_sql_text_length（默认 1024）限制，超长语句由 MySQL 侧截断。
 *
 * <p>与 {@link TopSqlItem}（digest 聚合、小时级）互补：本项提供"哪几条真实语句慢"，
 * digest 聚合提供"哪类语句整体最耗时"。分钟级采集尽量缩小 history 环形缓冲被覆盖的窗口，
 * 但 history 每线程仅 10 条，高并发下样本仍为抽样非全量。
 * 查询本身轻量（history 表最多 线程数 × 10 行，且按耗时阈值过滤），分钟级频率对目标库无明显压力。
 *
 * <p>去重：{@link SlowSqlSampleWatermarkStore} 按 (threadId, eventId) 水位跳过已采事件。
 * 排除监控连接自身产生的语句。仅在支持 performance_schema 的版本（5.7/8.0）采集。
 *
 * <p>完整 SQL 回填：每轮先从 information_schema.PROCESSLIST 捕获"仍在执行且已超过
 * long_query_time"语句的完整 INFO 文本（不受 p_s 截断限制），缓存到
 * {@link FullSqlCaptureStore}；history 中的样本文本若被目标库截断（"..." 结尾），
 * 按 (threadId, 前缀) 匹配回填全文。
 */
@Component
public class SlowSqlSampleItem implements MySqlMetricItem {

    public static final String CODE = "slow_sql_sample";

    /** 单轮样本上限（按耗时降序截取）。 */
    private static final int MAX_SAMPLES_PER_ROUND = 100;

    /**
     * SQL 文本保护性上限（防止单条超长语句撑爆存储；列类型为 TEXT，正常语句不会触达）。
     * <p>注意：真正的截断通常发生在目标库侧——performance_schema 的 SQL_TEXT 受启动参数
     * {@code performance_schema_max_sql_text_length}（默认 1024 字符）限制，超长语句
     * 由 MySQL 截断并以 "..." 结尾。如需采集完整超长 SQL，需在目标库调大该参数并重启。
     */
    private static final int MAX_SQL_LEN = 65536;

    /** 皮秒 → 微秒。 */
    private static final long PS_TO_US = 1_000_000L;

    /** 在途完整 SQL 捕获单轮上限（长慢查询并发本就有限）。 */
    private static final int MAX_CAPTURES_PER_ROUND = 50;

    private final SlowSqlSampleWatermarkStore watermarkStore;
    private final FullSqlCaptureStore fullSqlCaptureStore;

    public SlowSqlSampleItem(SlowSqlSampleWatermarkStore watermarkStore,
                             FullSqlCaptureStore fullSqlCaptureStore) {
        this.watermarkStore = watermarkStore;
        this.fullSqlCaptureStore = fullSqlCaptureStore;
    }

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

        double longQueryTimeSeconds = readLongQueryTimeSeconds(conn);
        if (longQueryTimeSeconds <= 0) {
            // long_query_time=0 会把所有语句判为慢查询，样本失去意义，跳过
            return;
        }
        long thresholdPs = (long) (longQueryTimeSeconds * 1_000_000_000_000L);

        // 先捕获在途长慢查询的完整 SQL（供本轮/后续轮次回填 history 中被截断的样本文本）
        captureRunningFullSql(conn, instanceId, longQueryTimeSeconds);

        String sql = "SELECT h.THREAD_ID, h.EVENT_ID, t.PROCESSLIST_USER, t.PROCESSLIST_HOST, "
                + "h.CURRENT_SCHEMA, h.DIGEST, h.SQL_TEXT, "
                + "h.TIMER_WAIT, h.LOCK_TIME, h.ROWS_EXAMINED, h.ROWS_SENT, h.SORT_ROWS, "
                + "h.NO_INDEX_USED, h.CREATED_TMP_TABLES, h.CREATED_TMP_DISK_TABLES "
                + "FROM performance_schema.events_statements_history h "
                + "LEFT JOIN performance_schema.threads t ON t.THREAD_ID = h.THREAD_ID "
                + "WHERE h.SQL_TEXT IS NOT NULL "
                + "  AND h.TIMER_WAIT >= ? "
                + "  AND (t.PROCESSLIST_ID IS NULL OR t.PROCESSLIST_ID != CONNECTION_ID()) "
                + "ORDER BY h.TIMER_WAIT DESC "
                + "LIMIT " + MAX_SAMPLES_PER_ROUND;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            ps.setLong(1, thresholdPs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long threadId = rs.getLong("THREAD_ID");
                    long eventId = rs.getLong("EVENT_ID");
                    if (!watermarkStore.advanceIfNew(instanceId, threadId, eventId)) {
                        continue;
                    }
                    String sqlText = rs.getString("SQL_TEXT");
                    // 被目标库截断（"..." 结尾）时，尝试用在途捕获的完整文本回填
                    if (sqlText != null && sqlText.endsWith("...")) {
                        String fullSql = fullSqlCaptureStore.resolve(instanceId, threadId, sqlText);
                        if (fullSql != null) {
                            sqlText = fullSql;
                        }
                    }
                    if (sqlText != null && sqlText.length() > MAX_SQL_LEN) {
                        sqlText = sqlText.substring(0, MAX_SQL_LEN);
                    }
                    sink.addSlowSqlSample(new SlowSqlSamplePoint(
                            threadId,
                            eventId,
                            rs.getString("PROCESSLIST_USER"),
                            rs.getString("PROCESSLIST_HOST"),
                            rs.getString("CURRENT_SCHEMA"),
                            rs.getString("DIGEST"),
                            sqlText,
                            rs.getLong("TIMER_WAIT") / PS_TO_US,
                            rs.getLong("LOCK_TIME") / PS_TO_US,
                            rs.getLong("ROWS_EXAMINED"),
                            rs.getLong("ROWS_SENT"),
                            rs.getLong("SORT_ROWS"),
                            rs.getBoolean("NO_INDEX_USED"),
                            rs.getLong("CREATED_TMP_TABLES"),
                            rs.getLong("CREATED_TMP_DISK_TABLES"),
                            ts));
                }
            }
        }
    }

    /**
     * 从 information_schema.PROCESSLIST 捕获在途长慢查询的完整 SQL。
     * <p>INFO 列保留正在执行语句的完整文本（不受 performance_schema 截断限制），
     * 通过 performance_schema.threads 把 PROCESSLIST_ID 映射到 THREAD_ID，
     * 与 history 事件对齐。TIME 为整秒，阈值向上取整且至少 1 秒。
     * <p>捕获失败不影响主采集流程（如 threads 表无访问权限时静默跳过）。
     */
    private void captureRunningFullSql(Connection conn, long instanceId, double longQueryTimeSeconds) {
        int thresholdSeconds = (int) Math.max(1, Math.ceil(longQueryTimeSeconds));
        String sql = "SELECT t.THREAD_ID, p.INFO "
                + "FROM information_schema.PROCESSLIST p "
                + "JOIN performance_schema.threads t ON t.PROCESSLIST_ID = p.ID "
                + "WHERE p.ID != CONNECTION_ID() "
                + "  AND p.INFO IS NOT NULL "
                + "  AND UPPER(p.COMMAND) IN ('QUERY', 'EXECUTE') "
                + "  AND p.TIME >= ? "
                + "ORDER BY p.TIME DESC "
                + "LIMIT " + MAX_CAPTURES_PER_ROUND;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            ps.setInt(1, thresholdSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String info = rs.getString("INFO");
                    if (info == null || info.isBlank()) {
                        continue;
                    }
                    if (info.length() > MAX_SQL_LEN) {
                        info = info.substring(0, MAX_SQL_LEN);
                    }
                    fullSqlCaptureStore.put(instanceId, rs.getLong("THREAD_ID"), info);
                }
            }
        } catch (SQLException ignored) {
            // 在途捕获属增强能力，失败（权限不足/超时）不影响样本主链路
        }
    }

    /** 读取全局 long_query_time（秒，可为小数）。 */
    private static double readLongQueryTimeSeconds(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SELECT @@GLOBAL.long_query_time")) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }
}
