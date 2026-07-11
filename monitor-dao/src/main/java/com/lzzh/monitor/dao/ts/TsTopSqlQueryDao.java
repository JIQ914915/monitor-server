package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsTopSqlQueryMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Top SQL / 慢 SQL 指纹查询 DAO（metric_top_sql 读侧）。
 *
 * <p>metric_top_sql 每小时落一批 digest 周期增量，本 DAO 按 (schema_name, digest)
 * 聚合时间窗口内的增量，输出"窗口内 Top SQL 排名"。排序字段经白名单映射后拼入 SQL，
 * 杜绝外部原始输入进入 ORDER BY。
 */
@Repository
public class TsTopSqlQueryDao {

    /** 排序字段白名单：对外字段名 → SQL 聚合列名。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "totalTimerWait", "total_timer_wait",
            "execCount", "exec_count",
            "avgTimerWaitUs", "avg_timer_wait_us",
            "maxAvgTimerWaitUs", "max_avg_timer_wait_us",
            "rowsExamined", "rows_examined",
            "sortRows", "sort_rows",
            "lastSeen", "last_seen"
    );

    /** SQL 类型筛选白名单：类型 → digest_text 前缀匹配模式。 */
    private static final Map<String, String> SQL_TYPE_PREFIX = Map.of(
            "SELECT", "SELECT%",
            "INSERT", "INSERT%",
            "UPDATE", "UPDATE%",
            "DELETE", "DELETE%"
    );

    private final TsTopSqlQueryMapper mapper;

    public TsTopSqlQueryDao(TsTopSqlQueryMapper mapper) {
        this.mapper = mapper;
    }

    /** 指纹聚合分页查询条件（minAvgUs/maxAvgUs 为窗口平均耗时区间，微秒）。 */
    public record DigestQuery(Long instanceId, long from, long to,
                              String keyword, String schemaName, String sqlType,
                              Long minAvgUs, Long maxAvgUs, String sortField, boolean asc,
                              int pageNum, int pageSize) {
    }

    /** 指纹聚合行（窗口内增量聚合结果；诊断维度在 V99 前采集的历史行可能为 null，聚合后为 0）。 */
    public record DigestRow(String schemaName, String digest, String digestText,
                            long execCount, long totalTimerWaitPs, long avgTimerWaitUs,
                            long maxAvgTimerWaitUs, long rowsExamined, long rowsSent,
                            long lockTimePs, long sortRows, long noIndexUsed,
                            long tmpTables, long tmpDiskTables,
                            long firstSeenMillis, long lastSeenMillis) {
    }

    /** 单指纹趋势点（小时级采集周期增量）。 */
    public record DigestTrendPoint(long ts, long execCount, long avgTimerWaitUs, long rowsExamined) {
    }

    /** 采集周期明细行（每行 = 某 digest 在某个采集周期内的增量）。 */
    public record RecordRow(long collectTimeMillis, String schemaName, String digest, String digestText,
                            long execCount, long avgTimerWaitUs, long totalTimerWaitPs,
                            long rowsExamined, long rowsSent,
                            long lockTimePs, long sortRows, long noIndexUsed,
                            long tmpTables, long tmpDiskTables) {
    }

    /** 周期明细分页查询条件（digest 非空时按指纹过滤，schemaName 参与精确匹配）。 */
    public record RecordQuery(Long instanceId, long from, long to,
                              String sqlType, Long minAvgUs, Long maxAvgUs,
                              String digest, String schemaName,
                              int pageNum, int pageSize) {
    }

    /** 指纹聚合分页列表。 */
    public List<DigestRow> pageDigest(DigestQuery q) {
        int limit = Math.max(1, q.pageSize());
        int offset = Math.max(0, (q.pageNum() - 1) * limit);
        List<Map<String, Object>> rows = mapper.selectDigestPage(
                q.instanceId(), new Timestamp(q.from()), new Timestamp(q.to()),
                blankToNull(q.keyword()), blankToNull(q.schemaName()),
                sqlTypePrefix(q.sqlType()), q.minAvgUs(), q.maxAvgUs(),
                buildOrderBy(q.sortField(), q.asc()), limit, offset);

        List<DigestRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(new DigestRow(
                    (String) row.get("schema_name"),
                    (String) row.get("digest"),
                    (String) row.get("digest_text"),
                    toLong(row.get("exec_count")),
                    toLong(row.get("total_timer_wait")),
                    toLong(row.get("avg_timer_wait_us")),
                    toLong(row.get("max_avg_timer_wait_us")),
                    toLong(row.get("rows_examined")),
                    toLong(row.get("rows_sent")),
                    toLong(row.get("lock_time")),
                    toLong(row.get("sort_rows")),
                    toLong(row.get("no_index_used")),
                    toLong(row.get("tmp_tables")),
                    toLong(row.get("tmp_disk_tables")),
                    toMillis(row.get("first_seen")),
                    toMillis(row.get("last_seen"))));
        }
        return result;
    }

    /** 指纹聚合总数（与 pageDigest 同条件）。 */
    public long countDigest(DigestQuery q) {
        return mapper.countDigest(q.instanceId(), new Timestamp(q.from()), new Timestamp(q.to()),
                blankToNull(q.keyword()), blankToNull(q.schemaName()),
                sqlTypePrefix(q.sqlType()), q.minAvgUs(), q.maxAvgUs());
    }

    /** 窗口概览统计。 */
    public WindowStats queryWindowStats(Long instanceId, long from, long to) {
        Map<String, Object> row = mapper.selectWindowStats(instanceId, new Timestamp(from), new Timestamp(to));
        if (row == null || row.isEmpty()) {
            return new WindowStats(0, 0, 0, 0, 0, 0, 0);
        }
        return new WindowStats(
                toLong(row.get("digest_count")),
                toLong(row.get("total_exec_count")),
                toLong(row.get("total_timer_wait")),
                toLong(row.get("max_avg_timer_wait_us")),
                toLong(row.get("total_rows_examined")),
                toLong(row.get("no_index_digest_count")),
                toLong(row.get("tmp_table_digest_count")));
    }

    /** 采集周期明细分页。 */
    public List<RecordRow> pageRecords(RecordQuery q) {
        int limit = Math.max(1, q.pageSize());
        int offset = Math.max(0, (q.pageNum() - 1) * limit);
        List<Map<String, Object>> rows = mapper.selectRecordsPage(
                q.instanceId(), new Timestamp(q.from()), new Timestamp(q.to()),
                sqlTypePrefix(q.sqlType()), q.minAvgUs(), q.maxAvgUs(),
                blankToNull(q.digest()), blankToNull(q.schemaName()), limit, offset);
        List<RecordRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(new RecordRow(
                    toMillis(row.get("collect_time")),
                    (String) row.get("schema_name"),
                    (String) row.get("digest"),
                    (String) row.get("digest_text"),
                    toLong(row.get("delta_count")),
                    toLong(row.get("avg_timer_wait_us")),
                    toLong(row.get("delta_timer_wait")),
                    toLong(row.get("delta_rows_examined")),
                    toLong(row.get("delta_rows_sent")),
                    toLong(row.get("delta_lock_time")),
                    toLong(row.get("delta_sort_rows")),
                    toLong(row.get("delta_no_index_used")),
                    toLong(row.get("delta_tmp_tables")),
                    toLong(row.get("delta_tmp_disk_tables"))));
        }
        return result;
    }

    /** 采集周期明细总数。 */
    public long countRecords(RecordQuery q) {
        return mapper.countRecords(q.instanceId(), new Timestamp(q.from()), new Timestamp(q.to()),
                sqlTypePrefix(q.sqlType()), q.minAvgUs(), q.maxAvgUs(),
                blankToNull(q.digest()), blankToNull(q.schemaName()));
    }

    /** 单指纹在时间窗口内的聚合详情；窗口内无数据时返回 null。 */
    public DigestRow getDigestDetail(Long instanceId, String schemaName, String digest, long from, long to) {
        List<Map<String, Object>> rows = mapper.selectDigestDetail(
                instanceId, new Timestamp(from), new Timestamp(to), blankToNull(schemaName), digest);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new DigestRow(
                (String) row.get("schema_name"),
                (String) row.get("digest"),
                (String) row.get("digest_text"),
                toLong(row.get("exec_count")),
                toLong(row.get("total_timer_wait")),
                toLong(row.get("avg_timer_wait_us")),
                toLong(row.get("max_avg_timer_wait_us")),
                toLong(row.get("rows_examined")),
                toLong(row.get("rows_sent")),
                toLong(row.get("lock_time")),
                toLong(row.get("sort_rows")),
                toLong(row.get("no_index_used")),
                toLong(row.get("tmp_tables")),
                toLong(row.get("tmp_disk_tables")),
                toMillis(row.get("first_seen")),
                toMillis(row.get("last_seen")));
    }

    /** 单指纹小时级趋势。 */
    public List<DigestTrendPoint> queryDigestTrend(Long instanceId, String schemaName, String digest,
                                                   long from, long to) {
        List<Map<String, Object>> rows = mapper.selectDigestTrend(
                instanceId, blankToNull(schemaName), digest, new Timestamp(from), new Timestamp(to));
        List<DigestTrendPoint> points = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            points.add(new DigestTrendPoint(
                    toMillis(row.get("collect_time")),
                    toLong(row.get("delta_count")),
                    toLong(row.get("avg_timer_wait_us")),
                    toLong(row.get("delta_rows_examined"))));
        }
        return points;
    }

    /** 窗口内出现的库名列表。 */
    public List<String> listSchemaNames(Long instanceId, long from, long to) {
        return mapper.selectSchemaNames(instanceId, new Timestamp(from), new Timestamp(to));
    }

    /** 窗口概览统计结果。 */
    public record WindowStats(long digestCount, long totalExecCount, long totalTimerWaitPs,
                              long maxAvgTimerWaitUs, long totalRowsExamined,
                              long noIndexDigestCount, long tmpTableDigestCount) {
    }

    // ── 内部工具 ─────────────────────────────────────────────────────────────

    private static String buildOrderBy(String sortField, boolean asc) {
        String column = SORT_COLUMNS.getOrDefault(sortField, "total_timer_wait");
        return column + (asc ? " ASC" : " DESC") + ", digest ASC";
    }

    private static String sqlTypePrefix(String sqlType) {
        if (sqlType == null) {
            return null;
        }
        return SQL_TYPE_PREFIX.get(sqlType.toUpperCase());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static long toLong(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
    }

    private static long toMillis(Object val) {
        if (val instanceof Timestamp ts) {
            return ts.toInstant().toEpochMilli();
        }
        if (val instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant().toEpochMilli();
        }
        return 0L;
    }
}
