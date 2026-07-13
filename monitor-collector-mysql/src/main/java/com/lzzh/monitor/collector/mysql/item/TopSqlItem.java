package com.lzzh.monitor.collector.mysql.item;

import com.lzzh.monitor.collector.spi.model.CollectRequest;
import com.lzzh.monitor.collector.mysql.version.MySqlVersionAdapter;
import com.lzzh.monitor.collector.spi.model.TopSqlPoint;
import com.lzzh.monitor.common.enums.CollectFrequency;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

/**
 * 采集项：Top SQL / 语句摘要（§7.3 SQL 域，小时级）。
 *
 * <p>基于 performance_schema.events_statements_summary_by_digest 采集累积快照，
 * 采集侧通过 {@link TopSqlDeltaStore} 对比上一周期快照，计算本周期增量（delta_count、
 * avg_timer_wait_us 等），落差值到 metric_top_sql 表（P1-3）。
 * 首次采集无历史快照时跳过本轮不写入（无法计算增量）；P_S truncate / 实例重启导致回绕时同样跳过。
 *
 * <p>采用小时级采集：digest 快照属较重查询（§8.4），且 Top SQL 分析对小时粒度已足够，
 * 避免分钟级高频拉全量摘要拖慢目标库、放大存储。
 *
 * <p>版本适配：仅在支持 performance_schema 的版本（5.7 / 8.0）采集；5.6 P_S 能力有限，跳过。
 */
@Component
public class TopSqlItem implements MySqlMetricItem {

    public static final String CODE = "top_sql";

    /** Top N 限流上限。 */
    private static final int TOP_N = 100;

    /** 查询级超时（秒）：P_S digest 表可能较大，超时由数据库侧 KILL QUERY 处理。 */
    private static final int QUERY_TIMEOUT_SECONDS = 15;

    @Resource
    private TopSqlDeltaStore deltaStore;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Set<CollectFrequency> frequencies() {
        return Set.of(CollectFrequency.HOURLY);
    }

    @Override
    public void collect(Connection conn, CollectRequest request, MySqlVersionAdapter adapter, MetricSink sink)
            throws SQLException {
        if (!adapter.supportsPerformanceSchema()) {
            return;
        }
        long instanceId = request.getInstanceId();
        long ts = System.currentTimeMillis();

        String sql = "SELECT SCHEMA_NAME, DIGEST, DIGEST_TEXT, COUNT_STAR, "
                + "SUM_TIMER_WAIT, SUM_ROWS_EXAMINED, SUM_ROWS_SENT, "
                + "SUM_LOCK_TIME, SUM_SORT_ROWS, SUM_NO_INDEX_USED, "
                + "SUM_CREATED_TMP_TABLES, SUM_CREATED_TMP_DISK_TABLES "
                + "FROM performance_schema.events_statements_summary_by_digest "
                + "WHERE DIGEST IS NOT NULL "
                + "ORDER BY SUM_TIMER_WAIT DESC LIMIT " + TOP_N;

        try (Statement st = conn.createStatement()) {
            st.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    String schemaName = rs.getString("SCHEMA_NAME");
                    String digest     = rs.getString("DIGEST");
                    String digestText = rs.getString("DIGEST_TEXT");
                    BigInteger countStar = unsignedCounter(rs, "COUNT_STAR");
                    // performance_schema 的计时器是无符号 BIGINT，累计值可超过 Java Long。
                    // 使用 BigInteger 保留累计快照，差值计算完成后才收敛到落库字段的 signed BIGINT。
                    BigInteger sumTimer = unsignedCounter(rs, "SUM_TIMER_WAIT");
                    BigInteger rowsExam = unsignedCounter(rs, "SUM_ROWS_EXAMINED");
                    BigInteger rowsSent = unsignedCounter(rs, "SUM_ROWS_SENT");
                    BigInteger lockTime = unsignedCounter(rs, "SUM_LOCK_TIME");
                    BigInteger sortRows = unsignedCounter(rs, "SUM_SORT_ROWS");
                    BigInteger noIndexUsed = unsignedCounter(rs, "SUM_NO_INDEX_USED");
                    BigInteger tmpTables = unsignedCounter(rs, "SUM_CREATED_TMP_TABLES");
                    BigInteger tmpDiskTbls = unsignedCounter(rs, "SUM_CREATED_TMP_DISK_TABLES");

                    // 计算本周期差值（首次 / 回绕时返回 null）
                    TopSqlDeltaStore.Delta delta =
                            deltaStore.compute(instanceId, schemaName, digest,
                                    countStar, sumTimer, rowsExam, rowsSent,
                                    lockTime, sortRows, noIndexUsed, tmpTables, tmpDiskTbls);

                    TopSqlPoint point = (delta != null)
                            ? new TopSqlPoint(schemaName, digest, digestText,
                                    TopSqlDeltaStore.toSignedLongSaturated(countStar), TopSqlDeltaStore.toSignedLongSaturated(sumTimer),
                                    TopSqlDeltaStore.toSignedLongSaturated(rowsExam), TopSqlDeltaStore.toSignedLongSaturated(rowsSent),
                                    delta.deltaCount(), delta.deltaTimerWait(), delta.avgTimerWaitUs(),
                                    delta.deltaRowsExamined(), delta.deltaRowsSent(),
                                    delta.deltaLockTime(), delta.deltaSortRows(), delta.deltaNoIndexUsed(),
                                    delta.deltaTmpTables(), delta.deltaTmpDiskTables(),
                                    ts)
                            : new TopSqlPoint(schemaName, digest, digestText,
                                    TopSqlDeltaStore.toSignedLongSaturated(countStar), TopSqlDeltaStore.toSignedLongSaturated(sumTimer),
                                    TopSqlDeltaStore.toSignedLongSaturated(rowsExam), TopSqlDeltaStore.toSignedLongSaturated(rowsSent), ts);

                    // 首次采样时 hasDelta()=false，sink 仍接收（写入层判断是否落库）
                    sink.addTopSql(point);
                }
            }
        }
    }

    private static BigInteger unsignedCounter(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigInteger.ZERO : value.toBigInteger();
    }
}
