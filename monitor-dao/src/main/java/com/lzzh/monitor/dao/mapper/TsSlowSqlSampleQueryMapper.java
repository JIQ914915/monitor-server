package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 慢 SQL 真实执行样本查询（metric_slow_sql_sample）。
 */
@Mapper
public interface TsSlowSqlSampleQueryMapper {

    /** 样本分页（(thread_id, event_id) 去重兜底采集节点重启后的重复写入，取每组最新一条）。 */
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
    List<Map<String, Object>> selectDigestAggForCluster(@Param("instanceId") Long instanceId,
                                                        @Param("from") Timestamp from,
                                                        @Param("to") Timestamp to,
                                                        @Param("limit") int limit);
}
