package com.lzzh.monitor.dao.mapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
@Mapper
public interface SqlServerOperationsMapper {
 long countRestoreDrills(@Param("instanceId")Long instanceId);
 List<Map<String,Object>> selectRestoreDrills(@Param("instanceId")Long instanceId,@Param("limit")int limit,@Param("offset")long offset);
 int insertRestoreDrill(@Param("drill")Map<String,Object> drill);
}
