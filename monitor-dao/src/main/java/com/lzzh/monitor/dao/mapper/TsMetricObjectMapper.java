package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsMetricObjectMapper {

    List<Map<String, Object>> selectTopN(@Param("instanceId") Long instanceId,
                                         @Param("metricCode") String metricCode,
                                         @Param("limit") int limit);

    long countLatest(@Param("instanceId") Long instanceId,
                     @Param("metricCode") String metricCode);

    List<Map<String, Object>> selectPage(@Param("instanceId") Long instanceId,
                                         @Param("metricCode") String metricCode,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    List<Map<String, Object>> selectLatestByNames(@Param("instanceId") Long instanceId,
                                                  @Param("metricCode") String metricCode,
                                                  @Param("objectNames") Collection<String> objectNames);
}
