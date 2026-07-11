package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsMetricObjectMapper {

    @Select("SELECT object_name, object_type, value, collect_time "
            + "FROM ( "
            + "  SELECT DISTINCT ON (object_name) "
            + "         object_name, object_type, value, collect_time "
            + "  FROM metric_capacity_object "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND metric_code = #{metricCode} "
            + "    AND collect_time > NOW() - INTERVAL '2 hours' "
            + "  ORDER BY object_name, collect_time DESC "
            + ") latest "
            + "ORDER BY value DESC "
            + "LIMIT #{limit}")
    List<Map<String, Object>> selectTopN(@Param("instanceId") Long instanceId,
                                         @Param("metricCode") String metricCode,
                                         @Param("limit") int limit);

    @Select("SELECT COUNT(*) "
            + "FROM ( "
            + "  SELECT DISTINCT ON (object_name) object_name "
            + "  FROM metric_capacity_object "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND metric_code = #{metricCode} "
            + "    AND collect_time > NOW() - INTERVAL '2 hours' "
            + "  ORDER BY object_name, collect_time DESC "
            + ") latest")
    long countLatest(@Param("instanceId") Long instanceId,
                     @Param("metricCode") String metricCode);

    @Select("SELECT object_name, object_type, value, collect_time "
            + "FROM ( "
            + "  SELECT DISTINCT ON (object_name) "
            + "         object_name, object_type, value, collect_time "
            + "  FROM metric_capacity_object "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND metric_code = #{metricCode} "
            + "    AND collect_time > NOW() - INTERVAL '2 hours' "
            + "  ORDER BY object_name, collect_time DESC "
            + ") latest "
            + "ORDER BY value DESC "
            + "LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> selectPage(@Param("instanceId") Long instanceId,
                                         @Param("metricCode") String metricCode,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    @Select("<script>"
            + "SELECT object_name, object_type, value, collect_time "
            + "FROM ( "
            + "  SELECT DISTINCT ON (object_name) "
            + "         object_name, object_type, value, collect_time "
            + "  FROM metric_capacity_object "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND metric_code = #{metricCode} "
            + "    AND collect_time &gt; NOW() - INTERVAL '2 hours' "
            + "    AND object_name IN "
            + "    <foreach collection='objectNames' item='n' open='(' separator=',' close=')'>#{n}</foreach> "
            + "  ORDER BY object_name, collect_time DESC "
            + ") latest"
            + "</script>")
    List<Map<String, Object>> selectLatestByNames(@Param("instanceId") Long instanceId,
                                                  @Param("metricCode") String metricCode,
                                                  @Param("objectNames") Collection<String> objectNames);
}
