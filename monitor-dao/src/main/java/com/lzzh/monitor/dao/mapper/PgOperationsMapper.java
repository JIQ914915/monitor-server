package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface PgOperationsMapper {
    long countSnapshots(@Param("instanceId") Long instanceId,
        @Param("source") String source,@Param("category") String category,
        @Param("sqlState") String sqlState,@Param("database") String database,@Param("user") String user,
        @Param("keyword") String keyword,@Param("from") Timestamp from,@Param("to") Timestamp to);
    List<Map<String,Object>> selectSnapshots(@Param("instanceId") Long instanceId,
        @Param("source") String source,@Param("category") String category,
        @Param("sqlState") String sqlState,@Param("database") String database,@Param("user") String user,
        @Param("keyword") String keyword,@Param("from") Timestamp from,@Param("to") Timestamp to,
        @Param("limit") int limit,@Param("offset") long offset);
    List<Map<String,Object>> selectSnapshotSummary(@Param("instanceId") Long instanceId,@Param("from") Timestamp from);
    long countEvents(@Param("instanceId") Long instanceId,
        @Param("source") String source,@Param("category") String category,@Param("excludeAudit") boolean excludeAudit,
        @Param("sqlState") String sqlState,@Param("database") String database,@Param("user") String user,
        @Param("keyword") String keyword,@Param("from") Timestamp from,@Param("to") Timestamp to);
    List<Map<String,Object>> selectEvents(@Param("instanceId") Long instanceId,
        @Param("source") String source,@Param("category") String category,@Param("excludeAudit") boolean excludeAudit,
        @Param("sqlState") String sqlState,@Param("database") String database,@Param("user") String user,
        @Param("keyword") String keyword,@Param("from") Timestamp from,@Param("to") Timestamp to,
        @Param("limit") int limit,@Param("offset") long offset);
    List<Map<String,Object>> selectSummary(@Param("instanceId") Long instanceId,@Param("from") Timestamp from);
    long countRestoreDrills(@Param("instanceId") Long instanceId);
    List<Map<String,Object>> selectRestoreDrills(@Param("instanceId") Long instanceId,@Param("limit") int limit,@Param("offset") long offset);
    int insertRestoreDrill(@Param("drill") Map<String,Object> drill);
}
