package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface MySqlDiagnosticMapper {
    List<Map<String,Object>> selectConfigChanges(@Param("instanceId") Long instanceId,
                                                  @Param("since") Timestamp since,
                                                  @Param("limit") int limit);
    List<Map<String,Object>> selectConfigLatest(@Param("instanceId") Long instanceId);
    List<Map<String,Object>> selectNumericConfigChanges(@Param("instanceId") Long instanceId,
                                                        @Param("since") Timestamp since,
                                                        @Param("limit") int limit);
    List<Map<String,Object>> selectNumericConfigLatest(@Param("instanceId") Long instanceId);
    List<Map<String,Object>> selectAutoIncrementRisks(@Param("instanceId") Long instanceId,
                                                      @Param("limit") int limit);
    List<Map<String,Object>> selectTopSql(@Param("instanceId") Long instanceId,
                                          @Param("from") Timestamp from,
                                          @Param("to") Timestamp to,
                                          @Param("limit") int limit);
    Map<String,Object> selectLatestPlan(@Param("instanceId") Long instanceId,
                                        @Param("schemaName") String schemaName,
                                        @Param("sqlHash") String sqlHash);
    int insertPlan(@Param("plan") Map<String,Object> plan);
    List<Map<String,Object>> selectRecentPlans(@Param("instanceId") Long instanceId,
                                               @Param("from") Timestamp from,
                                               @Param("to") Timestamp to,
                                               @Param("limit") int limit);
    String selectLatestSecuritySnapshotHash(@Param("instanceId") Long instanceId);
    int insertSecuritySnapshot(@Param("snapshot") Map<String,Object> snapshot);
    List<Map<String,Object>> selectPlanHistory(@Param("instanceId") Long instanceId,
                                               @Param("schemaName") String schemaName,
                                               @Param("sqlHash") String sqlHash,
                                               @Param("limit") int limit);
}
