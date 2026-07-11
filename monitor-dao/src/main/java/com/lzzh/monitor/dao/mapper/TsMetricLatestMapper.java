package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TsMetricLatestMapper {

    @Select({
            "<script>",
            "SELECT DISTINCT ON (metric_code) metric_code, value",
            "FROM metric_data_1m",
            "WHERE instance_id = #{instanceId}",
            "  AND metric_code IN",
            "  <foreach collection='metricCodes' item='code' open='(' separator=',' close=')'>",
            "    #{code}",
            "  </foreach>",
            "  AND collect_time > NOW() - INTERVAL '10 minutes'",
            "ORDER BY metric_code, collect_time DESC",
            "</script>"
    })
    List<Map<String, Object>> selectLatestFrom1m(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") List<String> metricCodes);

    @Select({
            "<script>",
            "SELECT DISTINCT ON (metric_code) metric_code, value",
            "FROM metric_data_1h",
            "WHERE instance_id = #{instanceId}",
            "  AND metric_code IN",
            "  <foreach collection='metricCodes' item='code' open='(' separator=',' close=')'>",
            "    #{code}",
            "  </foreach>",
            "  AND collect_time > NOW() - INTERVAL '2 hours'",
            "ORDER BY metric_code, collect_time DESC",
            "</script>"
    })
    List<Map<String, Object>> selectLatestFrom1h(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") List<String> metricCodes);

    @Select({
            "<script>",
            "SELECT DISTINCT ON (metric_code) metric_code, value",
            "FROM metric_data_1d",
            "WHERE instance_id = #{instanceId}",
            "  AND metric_code IN",
            "  <foreach collection='metricCodes' item='code' open='(' separator=',' close=')'>",
            "    #{code}",
            "  </foreach>",
            "  AND collect_time > NOW() - INTERVAL '30 days'",
            "ORDER BY metric_code, collect_time DESC",
            "</script>"
    })
    List<Map<String, Object>> selectLatestFrom1d(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") List<String> metricCodes);
}
