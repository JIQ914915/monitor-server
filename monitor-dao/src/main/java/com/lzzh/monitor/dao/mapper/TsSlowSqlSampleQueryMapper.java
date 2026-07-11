package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 慢 SQL 真实执行样本查询（metric_slow_sql_sample）。
 * <p>注意：注解 {@code <script>} 内的 {@code <} 必须转义为 {@code &lt;}。
 */
@Mapper
public interface TsSlowSqlSampleQueryMapper {

    /** 样本行过滤条件（时间窗口 + SQL 类型 + 耗时区间 + 指纹）。 */
    String SAMPLE_FILTERS =
            "<if test='sqlTypePrefix != null'>"
            + "  AND upper(sql_text) LIKE #{sqlTypePrefix} "
            + "</if>"
            + "<if test='minExecUs != null'>"
            + "  AND exec_time_us &gt;= #{minExecUs} "
            + "</if>"
            + "<if test='maxExecUs != null'>"
            + "  AND exec_time_us &lt;= #{maxExecUs} "
            + "</if>"
            + "<if test='digest != null'>"
            + "  AND digest = #{digest} "
            + "</if>";

    /** 样本分页（(thread_id, event_id) 去重兜底采集节点重启后的重复写入，取每组最新一条）。 */
    @Select({
            "<script>",
            "SELECT thread_id, event_id, conn_user, conn_host, schema_name, digest, sql_text, ",
            "  exec_time_us, lock_time_us, rows_examined, rows_sent, sort_rows, ",
            "  no_index_used, tmp_tables, tmp_disk_tables, collect_time ",
            "FROM ( ",
            "  SELECT DISTINCT ON (thread_id, event_id) * ",
            "  FROM metric_slow_sql_sample ",
            "  WHERE instance_id = #{instanceId} ",
            "    AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ",
            SAMPLE_FILTERS,
            "  ORDER BY thread_id, event_id, collect_time DESC ",
            ") d ",
            "ORDER BY ${orderBy} ",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<Map<String, Object>> selectSamplesPage(@Param("instanceId") Long instanceId,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to,
                                                @Param("sqlTypePrefix") String sqlTypePrefix,
                                                @Param("minExecUs") Long minExecUs,
                                                @Param("maxExecUs") Long maxExecUs,
                                                @Param("digest") String digest,
                                                @Param("orderBy") String orderBy,
                                                @Param("limit") int limit,
                                                @Param("offset") int offset);

    /** 样本总数（与 selectSamplesPage 同条件同去重口径）。 */
    @Select({
            "<script>",
            "SELECT count(*) FROM ( ",
            "  SELECT DISTINCT thread_id, event_id ",
            "  FROM metric_slow_sql_sample ",
            "  WHERE instance_id = #{instanceId} ",
            "    AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ",
            SAMPLE_FILTERS,
            ") d",
            "</script>"
    })
    long countSamples(@Param("instanceId") Long instanceId,
                      @Param("from") Timestamp from,
                      @Param("to") Timestamp to,
                      @Param("sqlTypePrefix") String sqlTypePrefix,
                      @Param("minExecUs") Long minExecUs,
                      @Param("maxExecUs") Long maxExecUs,
                      @Param("digest") String digest);

    /**
     * 指纹聚类原料：窗口内按 digest 聚合的样本统计 + 最新一条代表 SQL 文本。
     * 结构相似度聚类（同语句类型 + 同表集合）在 Java 侧完成，这里只做 digest 级预聚合。
     */
    @Select({
            "<script>",
            "SELECT digest, ",
            "  count(*) AS sample_count, ",
            "  sum(exec_time_us) AS total_us, ",
            "  avg(exec_time_us) AS avg_us, ",
            "  max(exec_time_us) AS max_us, ",
            "  max(schema_name) AS schema_name, ",
            "  (array_agg(sql_text ORDER BY collect_time DESC))[1] AS sql_text ",
            "FROM metric_slow_sql_sample ",
            "WHERE instance_id = #{instanceId} ",
            "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} ",
            "  AND digest IS NOT NULL AND sql_text IS NOT NULL ",
            "GROUP BY digest ",
            "ORDER BY total_us DESC ",
            "LIMIT #{limit}",
            "</script>"
    })
    List<Map<String, Object>> selectDigestAggForCluster(@Param("instanceId") Long instanceId,
                                                        @Param("from") Timestamp from,
                                                        @Param("to") Timestamp to,
                                                        @Param("limit") int limit);
}
