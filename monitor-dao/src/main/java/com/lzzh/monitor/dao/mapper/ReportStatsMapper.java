package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("<script>"
            + "SELECT metric_code, "
            + "  avg(value) AS avg_value, "
            + "  max(value) AS max_value, "
            + "  min(value) AS min_value, "
            + "  count(*)   AS point_count "
            + "FROM metric_data_1m "
            + "WHERE instance_id = #{instanceId} "
            + "  AND metric_code IN "
            + "  <foreach collection='metricCodes' item='c' open='(' separator=',' close=')'>#{c}</foreach> "
            + "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} "
            + "GROUP BY metric_code"
            + "</script>")
    List<Map<String, Object>> selectMetricStats(@Param("instanceId") Long instanceId,
                                                @Param("metricCodes") List<String> metricCodes,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to);

    /** 多指标窗口求和（delta 类计数器合计，如认证失败/权限变更次数），按指标编码分组。 */
    @Select("<script>"
            + "SELECT metric_code, sum(value) AS sum_value "
            + "FROM metric_data_1m "
            + "WHERE instance_id = #{instanceId} "
            + "  AND metric_code IN "
            + "  <foreach collection='metricCodes' item='c' open='(' separator=',' close=')'>#{c}</foreach> "
            + "  AND collect_time &gt;= #{from} AND collect_time &lt;= #{to} "
            + "GROUP BY metric_code"
            + "</script>")
    List<Map<String, Object>> selectMetricSums(@Param("instanceId") Long instanceId,
                                               @Param("metricCodes") List<String> metricCodes,
                                               @Param("from") Timestamp from,
                                               @Param("to") Timestamp to);

    /** 单指标小时级均值序列（异常时段识别用），按小时升序。 */
    @Select("SELECT date_trunc('hour', collect_time) AS hour_ts, avg(value) AS avg_value "
            + "FROM metric_data_1m "
            + "WHERE instance_id = #{instanceId} "
            + "  AND metric_code = #{metricCode} "
            + "  AND collect_time >= #{from} AND collect_time <= #{to} "
            + "GROUP BY hour_ts ORDER BY hour_ts ASC "
            + "LIMIT 800")
    List<Map<String, Object>> selectHourlyAvg(@Param("instanceId") Long instanceId,
                                              @Param("metricCode") String metricCode,
                                              @Param("from") Timestamp from,
                                              @Param("to") Timestamp to);

    /** 窗口内告警事件按级别统计（限定实例范围）。 */
    @Select("<script>"
            + "SELECT rule_level, count(*) AS cnt, "
            + "  count(*) FILTER (WHERE status IN ('pending','confirmed','handling')) AS active_cnt "
            + "FROM alert_event "
            + "WHERE instance_id IN "
            + "  <foreach collection='instanceIds' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
            + "  AND trigger_time &gt;= #{from} AND trigger_time &lt;= #{to} "
            + "GROUP BY rule_level"
            + "</script>")
    List<Map<String, Object>> selectAlertLevelStats(@Param("instanceIds") List<Long> instanceIds,
                                                    @Param("from") Timestamp from,
                                                    @Param("to") Timestamp to);

    /** 窗口内告警事件按规则聚合 Top N（带触发指标编码，供根因画像匹配）。 */
    @Select("<script>"
            + "SELECT e.rule_name, e.rule_level, count(*) AS cnt, "
            + "  sum(COALESCE(e.trigger_count, 1)) AS trigger_total, "
            + "  max(r.metric_name) AS metric_code "
            + "FROM alert_event e "
            + "LEFT JOIN alert_rule r ON r.id = e.rule_id "
            + "WHERE e.instance_id IN "
            + "  <foreach collection='instanceIds' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
            + "  AND e.trigger_time &gt;= #{from} AND e.trigger_time &lt;= #{to} "
            + "GROUP BY e.rule_name, e.rule_level "
            + "ORDER BY cnt DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectAlertTopRules(@Param("instanceIds") List<Long> instanceIds,
                                                  @Param("from") Timestamp from,
                                                  @Param("to") Timestamp to,
                                                  @Param("limit") int limit);

    /** 窗口内告警事件按实例聚合 Top N（高频告警实例）。 */
    @Select("<script>"
            + "SELECT instance_id, instance_name, count(*) AS cnt, "
            + "  count(*) FILTER (WHERE rule_level IN ('level_1','level_2')) AS severe_cnt, "
            + "  count(*) FILTER (WHERE status IN ('pending','confirmed','handling')) AS active_cnt "
            + "FROM alert_event "
            + "WHERE instance_id IN "
            + "  <foreach collection='instanceIds' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
            + "  AND trigger_time &gt;= #{from} AND trigger_time &lt;= #{to} "
            + "GROUP BY instance_id, instance_name "
            + "ORDER BY cnt DESC "
            + "LIMIT #{limit}"
            + "</script>")
    List<Map<String, Object>> selectAlertTopInstances(@Param("instanceIds") List<Long> instanceIds,
                                                      @Param("from") Timestamp from,
                                                      @Param("to") Timestamp to,
                                                      @Param("limit") int limit);

    /** 窗口内告警事件状态汇总（total/active/recovered）。 */
    @Select("<script>"
            + "SELECT count(*) AS total, "
            + "  count(*) FILTER (WHERE status IN ('pending','confirmed','handling')) AS active_cnt, "
            + "  count(*) FILTER (WHERE status = 'recovered') AS recovered_cnt, "
            + "  count(*) FILTER (WHERE status = 'closed') AS closed_cnt "
            + "FROM alert_event "
            + "WHERE instance_id IN "
            + "  <foreach collection='instanceIds' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
            + "  AND trigger_time &gt;= #{from} AND trigger_time &lt;= #{to}"
            + "</script>")
    Map<String, Object> selectAlertSummary(@Param("instanceIds") List<Long> instanceIds,
                                           @Param("from") Timestamp from,
                                           @Param("to") Timestamp to);
}
