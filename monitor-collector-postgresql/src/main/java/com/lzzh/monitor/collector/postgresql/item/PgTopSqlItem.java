package com.lzzh.monitor.collector.postgresql.item;

import com.lzzh.monitor.collector.postgresql.version.PgVersionAdapter;
import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：PG Top SQL 指纹聚合（二期 B2，小时级，对标 MySQL TopSqlItem）。
 *
 * <p>基于 {@code pg_stat_statements}（queryid 即原生指纹）采集累积快照，
 * 经 {@link PgTopSqlDeltaStore} 差值后落既有 metric_top_sql 表，页面复用 Top SQL 榜单。
 * 扩展未安装/未加载时静默跳过（能力检测项 {@link PgExtensionsItem} 负责状态声明与安装引导）。
 *
 * <p><b>字段映射约定</b>（PG 语义 → metric_top_sql 列，单位与 MySQL 对齐避免服务端换算分叉）：
 * <ul>
 *   <li>digest ← queryid（十进制字符串）；digest_text ← query（归一化文本）；schema_name ← datname</li>
 *   <li>count_star/delta_count ← calls；总耗时 total_exec_time（毫秒）× 1e9 → 皮秒列 sum_timer_wait/delta_timer_wait</li>
 *   <li>avg_timer_wait_us ← 周期均耗时（毫秒×1000）</li>
 *   <li>rows_sent/delta_rows_sent ← rows；rows_examined/delta_rows_examined ← shared_blks_read（物理读块数，PG 无扫描行数概念）</li>
 *   <li>delta_tmp_disk_tables ← temp_blks_written（临时块写入，PG 的"落盘"信号）；其余诊断列置 0</li>
 * </ul>
 */
@Component
public class PgTopSqlItem implements PgMetricItem {

    public static final String CODE = "pg_top_sql";

    private static final int TOP_N = 100;
    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int MAX_QUERY_TEXT_LEN = 2000;

    /** 毫秒 → 皮秒（与 metric_top_sql 的 MySQL 皮秒口径对齐）。 */
    private static final double MS_TO_PS = 1_000_000_000d;

    @Resource
    private PgTopSqlDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, PgVersionAdapter adapter, PgMetricSink sink)
            throws SQLException {
        if (!extensionReadable(conn)) {
            return;
        }
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();

        // 13+ 列名已统一（total_exec_time）；只取当前可见库的语句（监控账号有 pg_monitor 可见全部）
        String sql = "SELECT d.datname, s.queryid, s.query, s.calls, s.total_exec_time, s.rows, "
                + "s.shared_blks_read, s.shared_blks_hit, s.temp_blks_written "
                + "FROM pg_stat_statements s "
                + "LEFT JOIN pg_database d ON d.oid = s.dbid "
                + "WHERE s.queryid IS NOT NULL "
                + "ORDER BY s.total_exec_time DESC LIMIT " + TOP_N;

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String datname = rs.getString("datname");
                    long queryId = rs.getLong("queryid");
                    String queryText = rs.getString("query");
                    long calls = rs.getLong("calls");
                    double totalExecMs = rs.getDouble("total_exec_time");
                    long rows = rs.getLong("rows");
                    long sharedRead = rs.getLong("shared_blks_read");
                    long sharedHit = rs.getLong("shared_blks_hit");
                    long tempWritten = rs.getLong("temp_blks_written");

                    if (queryText != null && queryText.length() > MAX_QUERY_TEXT_LEN) {
                        queryText = queryText.substring(0, MAX_QUERY_TEXT_LEN);
                    }

                    PgTopSqlDeltaStore.Delta delta = deltaStore.compute(instanceId, datname, queryId,
                            calls, totalExecMs, rows, sharedRead, sharedHit, tempWritten);

                    long sumTimerPs = (long) (totalExecMs * MS_TO_PS);
                    TopSqlPoint point = (delta != null)
                            ? new TopSqlPoint(datname, String.valueOf(queryId), queryText,
                                    calls, sumTimerPs, sharedRead, rows,
                                    delta.deltaCalls(),
                                    (long) (delta.deltaExecMs() * MS_TO_PS),
                                    (long) (delta.deltaExecMs() * 1000 / delta.deltaCalls()),
                                    delta.deltaSharedRead(), delta.deltaRows(),
                                    0L, 0L, 0L,
                                    0L, delta.deltaTempWritten(),
                                    ts)
                            : new TopSqlPoint(datname, String.valueOf(queryId), queryText,
                                    calls, sumTimerPs, sharedRead, rows, ts);
                    // 首次采样 hasDelta()=false，由写入层过滤
                    sink.addTopSql(point);
                }
            }
        }
    }

    /** 探测 pg_stat_statements 视图当前是否可读（扩展未安装/未加载时查询会报错）。 */
    private static boolean extensionReadable(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(5);
            try (ResultSet rs = st.executeQuery(
                    "SELECT 1 FROM pg_extension WHERE extname = 'pg_stat_statements'")) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
