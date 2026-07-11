package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MetricValueQueryMapper {

    @Select("SELECT value FROM metric_data_1m "
            + "WHERE instance_id = #{instanceId} AND metric_code = #{metricCode} "
            + "AND collect_time >= now() - make_interval(mins => #{windowMinutes}) "
            + "ORDER BY collect_time DESC LIMIT 1")
    Double selectLatest1m(@Param("instanceId") Long instanceId,
                          @Param("metricCode") String metricCode,
                          @Param("windowMinutes") int windowMinutes);

    @Select("SELECT value FROM metric_data_1h "
            + "WHERE instance_id = #{instanceId} AND metric_code = #{metricCode} "
            + "AND collect_time >= now() - make_interval(hours => #{windowHours}) "
            + "ORDER BY collect_time DESC LIMIT 1")
    Double selectLatest1h(@Param("instanceId") Long instanceId,
                          @Param("metricCode") String metricCode,
                          @Param("windowHours") int windowHours);

    @Select("SELECT value FROM metric_data_1d "
            + "WHERE instance_id = #{instanceId} AND metric_code = #{metricCode} "
            + "AND collect_time >= now() - make_interval(days => #{windowDays}) "
            + "ORDER BY collect_time DESC LIMIT 1")
    Double selectLatest1d(@Param("instanceId") Long instanceId,
                          @Param("metricCode") String metricCode,
                          @Param("windowDays") int windowDays);

    /**
     * 取"offsetMinutes 分钟前"附近的历史值（环比基准点）：
     * 在 [now-offset-tolerance, now-offset+tolerance] 窗口内取最新一点。
     */
    @Select("SELECT value FROM metric_data_1m "
            + "WHERE instance_id = #{instanceId} AND metric_code = #{metricCode} "
            + "AND collect_time >= now() - make_interval(mins => #{offsetMinutes} + #{toleranceMinutes}) "
            + "AND collect_time <= now() - make_interval(mins => #{offsetMinutes} - #{toleranceMinutes}) "
            + "ORDER BY collect_time DESC LIMIT 1")
    Double selectValueAt1m(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("offsetMinutes") int offsetMinutes,
                           @Param("toleranceMinutes") int toleranceMinutes);

    @Select("SELECT value FROM metric_data_1h "
            + "WHERE instance_id = #{instanceId} AND metric_code = #{metricCode} "
            + "AND collect_time >= now() - make_interval(mins => #{offsetMinutes} + #{toleranceMinutes}) "
            + "AND collect_time <= now() - make_interval(mins => #{offsetMinutes} - #{toleranceMinutes}) "
            + "ORDER BY collect_time DESC LIMIT 1")
    Double selectValueAt1h(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("offsetMinutes") int offsetMinutes,
                           @Param("toleranceMinutes") int toleranceMinutes);

    @Select("SELECT value FROM metric_data_1d "
            + "WHERE instance_id = #{instanceId} AND metric_code = #{metricCode} "
            + "AND collect_time >= now() - make_interval(mins => #{offsetMinutes} + #{toleranceMinutes}) "
            + "AND collect_time <= now() - make_interval(mins => #{offsetMinutes} - #{toleranceMinutes}) "
            + "ORDER BY collect_time DESC LIMIT 1")
    Double selectValueAt1d(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("offsetMinutes") int offsetMinutes,
                           @Param("toleranceMinutes") int toleranceMinutes);
}
