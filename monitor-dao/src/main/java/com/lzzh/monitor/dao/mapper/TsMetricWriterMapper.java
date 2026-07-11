package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsMetricWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsMetricWriterMapper {

    @Insert({
            "<script>",
            "INSERT INTO metric_data_1m (instance_id, metric_code, value, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.value}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "ON CONFLICT (instance_id, metric_code, collect_time) DO UPDATE",
            "SET value = EXCLUDED.value",
            "</script>"
    })
    int upsertMetric1m(@Param("items") List<TsMetricWriter.TsMetricPoint> items);

    @Insert({
            "<script>",
            "INSERT INTO metric_data_1h (instance_id, metric_code, value, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.value}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "ON CONFLICT (instance_id, metric_code, collect_time) DO UPDATE",
            "SET value = EXCLUDED.value",
            "</script>"
    })
    int upsertMetric1h(@Param("items") List<TsMetricWriter.TsMetricPoint> items);

    @Insert({
            "<script>",
            "INSERT INTO metric_data_1d (instance_id, metric_code, value, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.value}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "ON CONFLICT (instance_id, metric_code, collect_time) DO UPDATE",
            "SET value = EXCLUDED.value",
            "</script>"
    })
    int upsertMetric1d(@Param("items") List<TsMetricWriter.TsMetricPoint> items);
}
