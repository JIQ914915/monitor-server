package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface TsMetricTrendMapper {

    List<Map<String, Object>> selectTrend1m(@Param("instanceId") Long instanceId,
                                            @Param("metricCode") String metricCode,
                                            @Param("from") Timestamp from,
                                            @Param("to") Timestamp to);

    /** 小时级原始表：仅天生按小时采集的指标（容量 / binlog / 错误日志统计）有数据。 */
    List<Map<String, Object>> selectTrend1h(@Param("instanceId") Long instanceId,
                                            @Param("metricCode") String metricCode,
                                            @Param("from") Timestamp from,
                                            @Param("to") Timestamp to);

    /**
     * 分钟级指标的小时级降采样：查 TimescaleDB 连续聚合 metric_data_1h_cagg（V22），
     * 取 avg_value 作为小时值。cagg 保留 180 天，覆盖超出 1m 表 30 天保留期的历史区间。
     */
    List<Map<String, Object>> selectTrend1hCagg(@Param("instanceId") Long instanceId,
                                                @Param("metricCode") String metricCode,
                                                @Param("from") Timestamp from,
                                                @Param("to") Timestamp to);

    /**
     * 分钟级指标的小时级现算降采样（date_trunc 纯 PG 语法）：
     * 用于补齐 cagg 刷新滞后的尾部窗口，以及无 TimescaleDB 环境的整段兜底。
     */
    List<Map<String, Object>> selectTrend1hAggFrom1m(@Param("instanceId") Long instanceId,
                                                     @Param("metricCode") String metricCode,
                                                     @Param("from") Timestamp from,
                                                     @Param("to") Timestamp to);

    /** 连续聚合视图是否存在（纯 PG 开发环境无 TimescaleDB 时为 false）。 */
    @Select("SELECT to_regclass('metric_data_1h_cagg') IS NOT NULL")
    boolean caggExists();
}
