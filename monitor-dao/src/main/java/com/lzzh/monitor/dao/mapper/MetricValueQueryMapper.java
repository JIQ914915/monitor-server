package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MetricValueQueryMapper {

    Double selectLatest1m(@Param("instanceId") Long instanceId,
                          @Param("metricCode") String metricCode,
                          @Param("windowMinutes") int windowMinutes);

    Double selectLatest1h(@Param("instanceId") Long instanceId,
                          @Param("metricCode") String metricCode,
                          @Param("windowHours") int windowHours);

    Double selectLatest1d(@Param("instanceId") Long instanceId,
                          @Param("metricCode") String metricCode,
                          @Param("windowDays") int windowDays);

    /**
     * 取"offsetMinutes 分钟前"附近的历史值（环比基准点）：
     * 在 [now-offset-tolerance, now-offset+tolerance] 窗口内取最新一点。
     */
    Double selectValueAt1m(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("offsetMinutes") int offsetMinutes,
                           @Param("toleranceMinutes") int toleranceMinutes);

    Double selectValueAt1h(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("offsetMinutes") int offsetMinutes,
                           @Param("toleranceMinutes") int toleranceMinutes);

    Double selectValueAt1d(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("offsetMinutes") int offsetMinutes,
                           @Param("toleranceMinutes") int toleranceMinutes);
}
