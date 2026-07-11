package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * Top SQL / 慢 SQL 指纹查询 Mapper（metric_top_sql 表读侧）。
 *
 * <p>表中每小时写入一批 digest 周期增量（delta_count / delta_timer_wait 等），
 * 查询侧按 (schema_name, digest) 聚合时间窗口内的增量，得到"该窗口内的 Top SQL 排名"。
 * <p>orderBy 由 DAO 层白名单映射生成（仅允许固定列名 + ASC/DESC），不接收外部原始输入。
 */
@Mapper
public interface TsTopSqlQueryMapper {

    /** 指纹聚合分页列表（窗口内增量聚合，排序表达式由 DAO 白名单生成）。 */
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
    long countDigest(@Param("instanceId") Long instanceId,
                     @Param("from") Timestamp from,
                     @Param("to") Timestamp to,
                     @Param("keyword") String keyword,
                     @Param("schemaName") String schemaName,
                     @Param("sqlTypePrefix") String sqlTypePrefix,
                     @Param("minAvgUs") Long minAvgUs,
                     @Param("maxAvgUs") Long maxAvgUs);

    /** 时间窗口概览统计（对指纹聚合结果再汇总）。 */
    Map<String, Object> selectWindowStats(@Param("instanceId") Long instanceId,
                                          @Param("from") Timestamp from,
                                          @Param("to") Timestamp to);

    /** 慢SQL采集周期明细分页（每行 = 某 digest 在某个采集周期内的增量，按采集时间倒序）。 */
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
    long countRecords(@Param("instanceId") Long instanceId,
                      @Param("from") Timestamp from,
                      @Param("to") Timestamp to,
                      @Param("sqlTypePrefix") String sqlTypePrefix,
                      @Param("minAvgUs") Long minAvgUs,
                      @Param("maxAvgUs") Long maxAvgUs,
                      @Param("digest") String digest,
                      @Param("targetSchema") String targetSchema);

    /** 单指纹在时间窗口内的聚合详情（与 selectDigestPage 同口径，定位单条指纹）。 */
    List<Map<String, Object>> selectDigestDetail(@Param("instanceId") Long instanceId,
                                                 @Param("from") Timestamp from,
                                                 @Param("to") Timestamp to,
                                                 @Param("targetSchema") String targetSchema,
                                                 @Param("digest") String digest);

    /** 单指纹小时级趋势（执行次数 / 平均耗时 / 扫描行数，按采集时间升序）。 */
    List<Map<String, Object>> selectDigestTrend(@Param("instanceId") Long instanceId,
                                                @Param("schemaName") String schemaName,
                                                @Param("digest") String digest,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to);

    /** 窗口内出现过的库名列表（筛选下拉用）。 */
    List<String> selectSchemaNames(@Param("instanceId") Long instanceId,
                                   @Param("from") Timestamp from,
                                   @Param("to") Timestamp to);
}
