package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TsTableGrowthMapper {

    List<Map<String, Object>> selectTopN(@Param("instanceId") Long instanceId,
                                         @Param("metricCode") String metricCode,
                                         @Param("limit") int limit);
}
