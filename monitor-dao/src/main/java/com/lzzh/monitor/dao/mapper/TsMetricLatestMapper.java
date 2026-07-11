package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TsMetricLatestMapper {

    List<Map<String, Object>> selectLatestFrom1m(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") List<String> metricCodes);

    List<Map<String, Object>> selectLatestFrom1h(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") List<String> metricCodes);

    List<Map<String, Object>> selectLatestFrom1d(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") List<String> metricCodes);
}
