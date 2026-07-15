package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface PgDiagnosticMapper {
    List<Map<String, Object>> selectQueryAnalytics(
            @Param("instanceId") Long instanceId, @Param("from") Timestamp from, @Param("to") Timestamp to,
            @Param("database") String database, @Param("user") String user, @Param("queryId") String queryId,
            @Param("orderBy") String orderBy, @Param("limit") int limit);
    List<Map<String, Object>> selectRegressionCandidates(
            @Param("instanceId") Long instanceId,
            @Param("currentFrom") Timestamp currentFrom, @Param("currentTo") Timestamp currentTo,
            @Param("baselineFrom") Timestamp baselineFrom, @Param("baselineTo") Timestamp baselineTo);
    int upsertRegression(@Param("event") Map<String, Object> event);
    List<Map<String, Object>> selectRegressionEvents(@Param("instanceId") Long instanceId, @Param("limit") int limit);
    Map<String, Object> selectLatestPlan(@Param("instanceId") Long instanceId,
                                         @Param("database") String database, @Param("queryId") String queryId);
    int insertPlan(@Param("plan") Map<String, Object> plan);
    List<Map<String, Object>> selectPlanHistory(@Param("instanceId") Long instanceId,
                                                @Param("database") String database,
                                                @Param("queryId") String queryId, @Param("limit") int limit);
}