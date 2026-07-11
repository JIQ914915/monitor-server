package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 基线学习查询（§10.3 / §15.4.6 基线学习与异常检测）。
 *
 * <p>"业务时段基线"取历史同小时数据：同一实例、同一指标，过去 N 天中每天同一小时的
 * 小时均值序列。基线均值/波动由 Java 侧计算（样本量小，无需下推）。
 */
@Mapper
public interface TsBaselineQueryMapper {

    /**
     * 过去 lookbackDays 天内、每天同一小时（hourOfDay，0-23，服务器时区）的小时均值序列。
     * 返回列：bucket（该小时起点）、avg_value。不含当前未走完的小时。
     */
    List<Map<String, Object>> selectSameHourAvgs(@Param("instanceId") Long instanceId,
                                                 @Param("metricCode") String metricCode,
                                                 @Param("hourOfDay") int hourOfDay,
                                                 @Param("lookbackDays") int lookbackDays);

    /** 最近 minutes 分钟的均值（当前观测值）；无数据返回 null。 */
    Double selectRecentAvg(@Param("instanceId") Long instanceId,
                           @Param("metricCode") String metricCode,
                           @Param("minutes") int minutes);
}
