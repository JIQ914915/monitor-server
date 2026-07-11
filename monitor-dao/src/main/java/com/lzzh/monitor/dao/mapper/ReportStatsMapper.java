package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 报告生成聚合查询（§11.9 报告中心）。
 * <p>直查 metric_data_1m 做窗口聚合（单实例单指标 30 天约 4.3 万行，
 * DB 端聚合在当前规模下开销可接受，不引入额外物化视图）。
 */
@Mapper
public interface ReportStatsMapper {

    /** 多指标窗口统计（均值/最大/最小），按指标编码分组。 */
    List<Map<String, Object>> selectMetricStats(@Param("instanceId") Long instanceId,
                                                @Param("metricCodes") List<String> metricCodes,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to);

    /** 多指标窗口求和（delta 类计数器合计，如认证失败/权限变更次数），按指标编码分组。 */
    List<Map<String, Object>> selectMetricSums(@Param("instanceId") Long instanceId,
                                               @Param("metricCodes") List<String> metricCodes,
                                               @Param("from") Timestamp from,
                                               @Param("to") Timestamp to);

    /** 单指标小时级均值序列（异常时段识别用），按小时升序。 */
    List<Map<String, Object>> selectHourlyAvg(@Param("instanceId") Long instanceId,
                                              @Param("metricCode") String metricCode,
                                              @Param("from") Timestamp from,
                                              @Param("to") Timestamp to);

    /** 窗口内告警事件按级别统计（限定实例范围）。 */
    List<Map<String, Object>> selectAlertLevelStats(@Param("instanceIds") List<Long> instanceIds,
                                                    @Param("from") Timestamp from,
                                                    @Param("to") Timestamp to);

    /** 窗口内告警事件按规则聚合 Top N（带触发指标编码，供根因画像匹配）。 */
    List<Map<String, Object>> selectAlertTopRules(@Param("instanceIds") List<Long> instanceIds,
                                                  @Param("from") Timestamp from,
                                                  @Param("to") Timestamp to,
                                                  @Param("limit") int limit);

    /** 窗口内告警事件按实例聚合 Top N（高频告警实例）。 */
    List<Map<String, Object>> selectAlertTopInstances(@Param("instanceIds") List<Long> instanceIds,
                                                      @Param("from") Timestamp from,
                                                      @Param("to") Timestamp to,
                                                      @Param("limit") int limit);

    /** 窗口内告警事件状态汇总（total/active/recovered）。 */
    Map<String, Object> selectAlertSummary(@Param("instanceIds") List<Long> instanceIds,
                                           @Param("from") Timestamp from,
                                           @Param("to") Timestamp to);
}
