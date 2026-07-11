package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsCapacityGrowthMapper {

    @Select("SELECT current_day, current_bytes, prev_week_bytes, growth_bytes, growth_rate_pct "
            + "FROM capacity_weekly_growth "
            + "WHERE instance_id = #{instanceId} "
            + "  AND current_day >= #{since} "
            + "ORDER BY current_day ASC")
    List<Map<String, Object>> selectGrowthTrend(@Param("instanceId") Long instanceId,
                                                @Param("since") Date since);
}
