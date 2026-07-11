package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsCapacityGrowthMapper {

    List<Map<String, Object>> selectGrowthTrend(@Param("instanceId") Long instanceId,
                                                @Param("since") Date since);
}
