package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsTextReaderMapper {

    List<Map<String, Object>> selectLatestFrom1m(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") Collection<String> metricCodes);

    List<Map<String, Object>> selectLatestFrom1h(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") Collection<String> metricCodes);

    List<Map<String, Object>> selectLatestFrom1d(@Param("instanceId") Long instanceId,
                                                 @Param("metricCodes") Collection<String> metricCodes);

    List<Map<String, Object>> selectHistoryFrom1d(@Param("instanceId") Long instanceId,
                                                  @Param("metricCode") String metricCode);

    List<Map<String, Object>> selectRangeFrom1m(@Param("instanceId") Long instanceId,
                                                @Param("metricCode") String metricCode,
                                                @Param("fromMs") long fromMs,
                                                @Param("toMs") long toMs);
}
