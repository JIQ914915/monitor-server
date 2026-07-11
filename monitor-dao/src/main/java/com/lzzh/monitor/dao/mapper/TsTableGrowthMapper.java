package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TsTableGrowthMapper {

    @Select("SELECT a.object_name, a.object_type, "
            + "       a.value AS current_bytes, "
            + "       b.value AS prev_week_bytes, "
            + "       (a.value - b.value) AS growth_bytes, "
            + "       CASE WHEN b.value IS NOT NULL AND b.value > 0 "
            + "            THEN ROUND(((a.value - b.value) / b.value * 100)::NUMERIC, 2) "
            + "            ELSE NULL "
            + "       END AS growth_rate_pct "
            + "FROM ( "
            + "  SELECT DISTINCT ON (object_name) object_name, object_type, value "
            + "  FROM metric_capacity_object "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND metric_code = #{metricCode} "
            + "    AND collect_time > NOW() - INTERVAL '2 hours' "
            + "  ORDER BY object_name, collect_time DESC "
            + ") a "
            + "LEFT JOIN ( "
            + "  SELECT DISTINCT ON (object_name) object_name, value "
            + "  FROM metric_capacity_object "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND metric_code = #{metricCode} "
            + "    AND collect_time > NOW() - INTERVAL '7 days 2 hours' "
            + "    AND collect_time <= NOW() - INTERVAL '6 days 22 hours' "
            + "  ORDER BY object_name, collect_time DESC "
            + ") b ON a.object_name = b.object_name "
            + "ORDER BY growth_bytes DESC NULLS LAST, a.value DESC "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectTopN(@Param("instanceId") Long instanceId,
                                         @Param("metricCode") String metricCode,
                                         @Param("limit") int limit);
}
