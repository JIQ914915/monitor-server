package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TsParamQueryMapper {

    List<Map<String, Object>> selectLatestNumeric(@Param("instanceId") Long instanceId,
                                                  @Param("metricCodes") List<String> metricCodes);

    List<Map<String, Object>> selectLatestText(@Param("instanceId") Long instanceId,
                                               @Param("metricCodes") List<String> metricCodes);
}
