package com.lzzh.monitor.dao.ts;

import com.lzzh.monitor.dao.mapper.TsSlowSqlSampleQueryMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 慢 SQL 真实执行样本查询 DAO。 */
@Repository
public class TsSlowSqlSampleQueryDao {

    private final TsSlowSqlSampleQueryMapper mapper;

    public TsSlowSqlSampleQueryDao(TsSlowSqlSampleQueryMapper mapper) {
        this.mapper = mapper;
    }

    /** 样本行。 */
    public record SampleRow(long threadId, long eventId, String connUser, String connHost,
                            String schemaName, String digest, String sqlText,
                            long execTimeUs, long lockTimeUs, long rowsExamined, long rowsSent,
                            long sortRows, boolean noIndexUsed, long tmpTables, long tmpDiskTables,
                            long collectTimeMillis) {
    }

    /** 样本分页查询条件。 */
    public record SampleQuery(Long instanceId, long from, long to,
                              String sqlType, Long minExecUs, Long maxExecUs, String digest,
                              String sortField, boolean asc,
                              int pageNum, int pageSize) {
    }

    /** 排序字段白名单：VO 字段名 → 排序列。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "execTimeUs", "exec_time_us",
            "rowsExamined", "rows_examined",
            "sortRows", "sort_rows",
            "collectTime", "collect_time"
    );

    public List<SampleRow> pageSamples(SampleQuery q) {
        int limit = Math.max(1, q.pageSize());
        int offset = Math.max(0, (q.pageNum() - 1) * limit);
        List<Map<String, Object>> rows = mapper.selectSamplesPage(
                q.instanceId(), new Timestamp(q.from()), new Timestamp(q.to()),
                sqlTypePrefix(q.sqlType()), q.minExecUs(), q.maxExecUs(), blankToNull(q.digest()),
                buildOrderBy(q.sortField(), q.asc()), limit, offset);
        List<SampleRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(new SampleRow(
                    toLong(row.get("thread_id")),
                    toLong(row.get("event_id")),
                    (String) row.get("conn_user"),
                    (String) row.get("conn_host"),
                    (String) row.get("schema_name"),
                    (String) row.get("digest"),
                    (String) row.get("sql_text"),
                    toLong(row.get("exec_time_us")),
                    toLong(row.get("lock_time_us")),
                    toLong(row.get("rows_examined")),
                    toLong(row.get("rows_sent")),
                    toLong(row.get("sort_rows")),
                    Boolean.TRUE.equals(row.get("no_index_used")),
                    toLong(row.get("tmp_tables")),
                    toLong(row.get("tmp_disk_tables")),
                    toMillis(row.get("collect_time"))));
        }
        return result;
    }

    public long countSamples(SampleQuery q) {
        return mapper.countSamples(q.instanceId(), new Timestamp(q.from()), new Timestamp(q.to()),
                sqlTypePrefix(q.sqlType()), q.minExecUs(), q.maxExecUs(), blankToNull(q.digest()));
    }

    /** 指纹聚类原料：digest 级预聚合行（含最新代表 SQL 文本）。 */
    public record DigestAggRow(String digest, long sampleCount, long totalUs, long avgUs, long maxUs,
                               String schemaName, String sqlText) {
    }

    public List<DigestAggRow> listDigestAggForCluster(Long instanceId, long from, long to, int limit) {
        List<Map<String, Object>> rows = mapper.selectDigestAggForCluster(
                instanceId, new Timestamp(from), new Timestamp(to), limit);
        List<DigestAggRow> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            result.add(new DigestAggRow(
                    (String) row.get("digest"),
                    toLong(row.get("sample_count")),
                    toLong(row.get("total_us")),
                    toLong(row.get("avg_us")),
                    toLong(row.get("max_us")),
                    (String) row.get("schema_name"),
                    (String) row.get("sql_text")));
        }
        return result;
    }

    /** 排序白名单校验，非法字段回落默认（耗时降序）；orderBy 只可能来自本方法，无注入风险。 */
    private static String buildOrderBy(String sortField, boolean asc) {
        String column = SORT_COLUMNS.getOrDefault(sortField, "exec_time_us");
        return column + (asc ? " ASC" : " DESC") + ", collect_time DESC";
    }

    /** SQL 类型 → sql_text 大写前缀 LIKE 模式；未知类型返回 null 不过滤。 */
    private static String sqlTypePrefix(String sqlType) {
        if (sqlType == null || sqlType.isBlank()) {
            return null;
        }
        String upper = sqlType.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "SELECT", "INSERT", "UPDATE", "DELETE" -> upper + "%";
            default -> null;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static long toMillis(Object v) {
        if (v instanceof Timestamp t) {
            return t.getTime();
        }
        if (v instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant().toEpochMilli();
        }
        if (v instanceof java.time.Instant i) {
            return i.toEpochMilli();
        }
        return 0L;
    }
}
