package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsTextReaderMapper {

    @Select({
            "<script>",
            "SELECT DISTINCT ON (metric_code) metric_code, value_text, collect_time",
            "FROM metric_text_data_1m",
            "WHERE instance_id = #{instanceId}",
            "  AND metric_code IN",
            "  <foreach collection='metricCodes' item='code' open='(' separator=',' close=')'>",
            "    #{code}",
            "  </foreach>",
            "  AND collect_time > NOW() - INTERVAL '30 minutes'",
            "ORDER BY metric_code, collect_time DESC",
            "</script>"
    })
    List<Map<String, Object>> selectLatestFrom1m(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") Collection<String> metricCodes);

    @Select({
            "<script>",
            "SELECT DISTINCT ON (metric_code) metric_code, value_text, collect_time",
            "FROM metric_text_data_1h",
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
                                                 @Param("metricCodes") Collection<String> metricCodes);

    @Select({
            "<script>",
            "SELECT DISTINCT ON (metric_code) metric_code, value_text, collect_time",
            "FROM metric_text_data_1d",
            "WHERE instance_id = #{instanceId}",
            "  AND metric_code IN",
            "  <foreach collection='metricCodes' item='code' open='(' separator=',' close=')'>",
            "    #{code}",
            "  </foreach>",
            "  AND collect_time > NOW() - INTERVAL '2 days'",
            "ORDER BY metric_code, collect_time DESC",
            "</script>"
    })
    List<Map<String, Object>> selectLatestFrom1d(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") Collection<String> metricCodes);

    @Select("SELECT metric_code, value_text, collect_time "
            + "FROM metric_text_data_1d "
            + "WHERE instance_id = #{instanceId} "
            + "  AND metric_code = #{metricCode} "
            + "ORDER BY collect_time DESC "
            + "LIMIT 100")
    List<Map<String, Object>> selectHistoryFrom1d(@Param("instanceId") Long instanceId,
                                                  @Param("metricCode") String metricCode);

    @Select("SELECT metric_code, value_text, collect_time "
            + "FROM metric_text_data_1m "
            + "WHERE instance_id = #{instanceId} "
            + "  AND metric_code = #{metricCode} "
            + "  AND collect_time >= to_timestamp(#{fromMs} / 1000.0) "
            + "  AND collect_time <= to_timestamp(#{toMs} / 1000.0) "
            + "ORDER BY collect_time ASC "
            + "LIMIT 2000")
    List<Map<String, Object>> selectRangeFrom1m(@Param("instanceId") Long instanceId,
                                                @Param("metricCode") String metricCode,
                                                @Param("fromMs") long fromMs,
                                                @Param("toMs") long toMs);
}
