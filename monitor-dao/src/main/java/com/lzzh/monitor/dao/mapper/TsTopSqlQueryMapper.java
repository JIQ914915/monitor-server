package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Top SQL / 慢 SQL 指纹查询 Mapper（metric_top_sql 表读侧）。
 *
 * <p>表中每小时写入一批 digest 周期增量（delta_count / delta_timer_wait 等），
 * 查询侧按 (schema_name, digest) 聚合时间窗口内的增量，得到"该窗口内的 Top SQL 排名"。
 * <p>orderBy 由 DAO 层白名单映射生成（仅允许固定列名 + ASC/DESC），不接收外部原始输入。
 * <p>注意：本类中用于 {@code <script>} 动态 SQL 的常量按 XML 解析，"<" 必须写成 &lt;。
 */
@Mapper
public interface TsTopSqlQueryMapper {

    String DIGEST_AGG_SELECT =
            "SELECT schema_name, digest, "
            + "  max(digest_text)          AS digest_text, "
            + "  sum(delta_count)          AS exec_count, "
            + "  sum(delta_timer_wait)     AS total_timer_wait, "
            // 皮秒 → 微秒需除以 1e6（delta_timer_wait 为皮秒）
            + "  CASE WHEN sum(delta_count) > 0 "
            + "       THEN sum(delta_timer_wait) / 1000000 / sum(delta_count) ELSE 0 END AS avg_timer_wait_us, "
            + "  max(avg_timer_wait_us)    AS max_avg_timer_wait_us, "
            + "  sum(delta_rows_examined)  AS rows_examined, "
            + "  sum(delta_rows_sent)      AS rows_sent, "
            + "  sum(delta_lock_time)      AS lock_time, "
            + "  sum(delta_sort_rows)      AS sort_rows, "
            + "  sum(delta_no_index_used)  AS no_index_used, "
            + "  sum(delta_tmp_tables)     AS tmp_tables, "
            + "  sum(delta_tmp_disk_tables) AS tmp_disk_tables, "
            + "  min(collect_time)         AS first_seen, "
            + "  max(collect_time)         AS last_seen "
            + "FROM metric_top_sql "
            + "WHERE instance_id = #{instanceId} "
            + "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ";

    String DIGEST_AGG_FILTERS =
            "<if test='keyword != null and keyword != \"\"'>"
            + "  AND digest_text ILIKE '%' || #{keyword} || '%' "
            + "</if>"
            + "<if test='schemaName != null and schemaName != \"\"'>"
            + "  AND schema_name = #{schemaName} "
            + "</if>"
            + "<if test='sqlTypePrefix != null'>"
            + "  AND upper(digest_text) LIKE #{sqlTypePrefix} "
            + "</if>";

    /** 平均耗时区间过滤（HAVING，作用于聚合后的窗口平均耗时，单位微秒）。 */
    String DIGEST_AGG_HAVING =
            "<if test='minAvgUs != null or maxAvgUs != null'>"
            + "HAVING 1 = 1 "
            + "<if test='minAvgUs != null'>"
            + "  AND CASE WHEN sum(delta_count) > 0 "
            + "      THEN sum(delta_timer_wait) / 1000000 / sum(delta_count) ELSE 0 END &gt;= #{minAvgUs} "
            + "</if>"
            + "<if test='maxAvgUs != null'>"
            + "  AND CASE WHEN sum(delta_count) > 0 "
            + "      THEN sum(delta_timer_wait) / 1000000 / sum(delta_count) ELSE 0 END &lt;= #{maxAvgUs} "
            + "</if>"
            + "</if>";

    /** 指纹聚合分页列表（窗口内增量聚合，排序表达式由 DAO 白名单生成）。 */
    @Select({
            "<script>",
            DIGEST_AGG_SELECT,
            DIGEST_AGG_FILTERS,
            "GROUP BY schema_name, digest ",
            DIGEST_AGG_HAVING,
            "ORDER BY ${orderBy} ",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<Map<String, Object>> selectDigestPage(@Param("instanceId") Long instanceId,
                                               @Param("from") Timestamp from,
                                               @Param("to") Timestamp to,
                                               @Param("keyword") String keyword,
                                               @Param("schemaName") String schemaName,
                                               @Param("sqlTypePrefix") String sqlTypePrefix,
                                               @Param("minAvgUs") Long minAvgUs,
                                               @Param("maxAvgUs") Long maxAvgUs,
                                               @Param("orderBy") String orderBy,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    /** 指纹聚合总数（与 selectDigestPage 同条件）。 */
    @Select({
            "<script>",
            "SELECT count(*) FROM (",
            "SELECT 1 ",
            "FROM metric_top_sql ",
            "WHERE instance_id = #{instanceId} ",
            "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ",
            DIGEST_AGG_FILTERS,
            "GROUP BY schema_name, digest ",
            DIGEST_AGG_HAVING,
            ") t",
            "</script>"
    })
    long countDigest(@Param("instanceId") Long instanceId,
                     @Param("from") Timestamp from,
                     @Param("to") Timestamp to,
                     @Param("keyword") String keyword,
                     @Param("schemaName") String schemaName,
                     @Param("sqlTypePrefix") String sqlTypePrefix,
                     @Param("minAvgUs") Long minAvgUs,
                     @Param("maxAvgUs") Long maxAvgUs);

    /** 时间窗口概览统计（对指纹聚合结果再汇总）。 */
    @Select("SELECT count(*)                       AS digest_count, "
            + "  COALESCE(sum(exec_count), 0)      AS total_exec_count, "
            + "  COALESCE(sum(total_wait), 0)      AS total_timer_wait, "
            + "  COALESCE(max(max_avg_us), 0)      AS max_avg_timer_wait_us, "
            + "  COALESCE(sum(rows_exam), 0)       AS total_rows_examined, "
            + "  count(*) FILTER (WHERE no_index_used > 0) AS no_index_digest_count, "
            + "  count(*) FILTER (WHERE tmp_tables > 0 OR tmp_disk_tables > 0) AS tmp_table_digest_count "
            + "FROM ("
            + "  SELECT sum(delta_count)          AS exec_count, "
            + "         sum(delta_timer_wait)     AS total_wait, "
            + "         max(avg_timer_wait_us)    AS max_avg_us, "
            + "         sum(delta_rows_examined)  AS rows_exam, "
            + "         COALESCE(sum(delta_no_index_used), 0)   AS no_index_used, "
            + "         COALESCE(sum(delta_tmp_tables), 0)      AS tmp_tables, "
            + "         COALESCE(sum(delta_tmp_disk_tables), 0) AS tmp_disk_tables "
            + "  FROM metric_top_sql "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND collect_time >= #{from} AND collect_time <= #{to} "
            + "  GROUP BY schema_name, digest"
            + ") t")
    Map<String, Object> selectWindowStats(@Param("instanceId") Long instanceId,
                                          @Param("from") Timestamp from,
                                          @Param("to") Timestamp to);

    /** 采集周期明细行过滤（作用于单行原始列，非聚合）。 */
    String RECORD_FILTERS =
            "<if test='sqlTypePrefix != null'>"
            + "  AND upper(digest_text) LIKE #{sqlTypePrefix} "
            + "</if>"
            + "<if test='minAvgUs != null'>"
            + "  AND avg_timer_wait_us &gt;= #{minAvgUs} "
            + "</if>"
            + "<if test='maxAvgUs != null'>"
            + "  AND avg_timer_wait_us &lt;= #{maxAvgUs} "
            + "</if>"
            + "<if test='digest != null'>"
            + "  AND digest = #{digest} "
            + "  AND schema_name IS NOT DISTINCT FROM #{targetSchema,jdbcType=VARCHAR} "
            + "</if>";

    /** 慢SQL采集周期明细分页（每行 = 某 digest 在某个采集周期内的增量，按采集时间倒序）。 */
    @Select({
            "<script>",
            "SELECT collect_time, schema_name, digest, digest_text, ",
            "  delta_count, avg_timer_wait_us, delta_timer_wait, ",
            "  delta_rows_examined, delta_rows_sent, ",
            "  delta_lock_time, delta_sort_rows, delta_no_index_used, ",
            "  delta_tmp_tables, delta_tmp_disk_tables ",
            "FROM metric_top_sql ",
            "WHERE instance_id = #{instanceId} ",
            "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ",
            RECORD_FILTERS,
            "ORDER BY collect_time DESC, avg_timer_wait_us DESC ",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<Map<String, Object>> selectRecordsPage(@Param("instanceId") Long instanceId,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to,
                                                @Param("sqlTypePrefix") String sqlTypePrefix,
                                                @Param("minAvgUs") Long minAvgUs,
                                                @Param("maxAvgUs") Long maxAvgUs,
                                                @Param("digest") String digest,
                                                @Param("targetSchema") String targetSchema,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    /** 慢SQL采集周期明细总数（与 selectRecordsPage 同条件）。 */
    @Select({
            "<script>",
            "SELECT count(*) FROM metric_top_sql ",
            "WHERE instance_id = #{instanceId} ",
            "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ",
            RECORD_FILTERS,
            "</script>"
    })
    long countRecords(@Param("instanceId") Long instanceId,
                      @Param("from") Timestamp from,
                      @Param("to") Timestamp to,
                      @Param("sqlTypePrefix") String sqlTypePrefix,
                      @Param("minAvgUs") Long minAvgUs,
                      @Param("maxAvgUs") Long maxAvgUs,
                      @Param("digest") String digest,
                      @Param("targetSchema") String targetSchema);

    /** 单指纹在时间窗口内的聚合详情（与 DIGEST_AGG_SELECT 同口径，定位单条指纹）。 */
    @Select({
            "<script>",
            DIGEST_AGG_SELECT,
            "  AND digest = #{digest} ",
            "  AND schema_name IS NOT DISTINCT FROM #{targetSchema,jdbcType=VARCHAR} ",
            "GROUP BY schema_name, digest ",
            "LIMIT 1",
            "</script>"
    })
    List<Map<String, Object>> selectDigestDetail(@Param("instanceId") Long instanceId,
                                                 @Param("from") Timestamp from,
                                                 @Param("to") Timestamp to,
                                                 @Param("targetSchema") String targetSchema,
                                                 @Param("digest") String digest);

    /** 单指纹小时级趋势（执行次数 / 平均耗时 / 扫描行数，按采集时间升序）。 */
    @Select("SELECT collect_time, delta_count, avg_timer_wait_us, delta_rows_examined "
            + "FROM metric_top_sql "
            + "WHERE instance_id = #{instanceId} "
            + "  AND digest = #{digest} "
            + "  AND schema_name IS NOT DISTINCT FROM #{schemaName,jdbcType=VARCHAR} "
            + "  AND collect_time >= #{from} AND collect_time <= #{to} "
            + "ORDER BY collect_time ASC "
            + "LIMIT 2000")
    List<Map<String, Object>> selectDigestTrend(@Param("instanceId") Long instanceId,
                                                @Param("schemaName") String schemaName,
                                                @Param("digest") String digest,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to);

    /** 窗口内出现过的库名列表（筛选下拉用）。 */
    @Select("SELECT DISTINCT schema_name "
            + "FROM metric_top_sql "
            + "WHERE instance_id = #{instanceId} "
            + "  AND collect_time >= #{from} AND collect_time <= #{to} "
            + "  AND schema_name IS NOT NULL "
            + "ORDER BY schema_name "
            + "LIMIT 200")
    List<String> selectSchemaNames(@Param("instanceId") Long instanceId,
                                   @Param("from") Timestamp from,
                                   @Param("to") Timestamp to);
}
