package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 采集项：InnoDB 引擎状态解析（§15.4.1 二期 P1，分钟级，5.6/5.7/8.0 通用）。
 *
 * <p>解析 {@code SHOW ENGINE INNODB STATUS} 输出：
 * <ul>
 *   <li>{@code mysql.innodb.history_list_length} —— TRANSACTIONS 段 "History list length N"：
 *       undo 历史链长度，purge 跟不上（长事务阻塞回收）时持续增长，是长事务危害最直观的量化指标，
 *       对无 INNODB_METRICS trx_rseg_history_len 默认开启的 5.6 尤其重要；</li>
 *   <li>{@code mysql.innodb.latest_deadlock} —— LATEST DETECTED DEADLOCK 段全文（文本覆盖变更）：
 *       最近一次死锁的事务/持锁/等锁现场，配合 mysql.innodb.deadlock_count 告警定位死锁根因。</li>
 * </ul>
 *
 * <p>需要 PROCESS 权限；输出文本最大约 64KB，语句本身开销很小，分钟级采集无压力。
 * 无死锁段（实例启动以来未发生过死锁）时不产出死锁文本。
 */
@Component
public class InnodbStatusItem implements MySqlMetricItem {

    public static final String CODE = "innodb_status";

    private static final String DEADLOCK_HEADER = "LATEST DETECTED DEADLOCK";
    private static final int MAX_DEADLOCK_TEXT = 8000;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        long ts = System.currentTimeMillis();
        String status = null;
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(DEFAULT_QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery("SHOW ENGINE INNODB STATUS")) {
                if (rs.next()) {
                    // 结果集三列：Type / Name / Status，状态全文在第三列
                    status = rs.getString(3);
                }
            }
        }
        if (status == null || status.isBlank()) {
            return;
        }

        Long historyListLength = parseHistoryListLength(status);
        if (historyListLength != null) {
            sink.addNumeric("mysql.innodb.history_list_length", historyListLength, ts);
        }

        String deadlock = extractDeadlockSection(status);
        if (deadlock != null && !deadlock.isBlank()) {
            sink.addText("mysql.innodb.latest_deadlock", deadlock, ts);
        }
    }

    /** 解析 "History list length N" 行。 */
    static Long parseHistoryListLength(String status) {
        int idx = status.indexOf("History list length");
        if (idx < 0) {
            return null;
        }
        int lineEnd = status.indexOf('\n', idx);
        String line = lineEnd > idx ? status.substring(idx, lineEnd) : status.substring(idx);
        String num = line.replaceAll("[^0-9]", "");
        if (num.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截取 LATEST DETECTED DEADLOCK 段：从段标题后一行到下一个分节横线
     * （"------------" 开头的行，即下一段的边框）为止。
     */
    static String extractDeadlockSection(String status) {
        int headerIdx = status.indexOf(DEADLOCK_HEADER);
        if (headerIdx < 0) {
            return null;
        }
        int contentStart = status.indexOf('\n', headerIdx);
        if (contentStart < 0) {
            return null;
        }
        // 跳过标题下方的分隔横线行
        contentStart = skipSeparatorLine(status, contentStart + 1);
        int sectionEnd = status.indexOf("\n------------", contentStart);
        String section = sectionEnd > contentStart
                ? status.substring(contentStart, sectionEnd)
                : status.substring(contentStart);
        section = section.strip();
        if (section.length() > MAX_DEADLOCK_TEXT) {
            section = section.substring(0, MAX_DEADLOCK_TEXT);
        }
        return section;
    }

    private static int skipSeparatorLine(String status, int pos) {
        if (pos < status.length() && status.charAt(pos) == '-') {
            int nl = status.indexOf('\n', pos);
            return nl < 0 ? pos : nl + 1;
        }
        return pos;
    }
}
