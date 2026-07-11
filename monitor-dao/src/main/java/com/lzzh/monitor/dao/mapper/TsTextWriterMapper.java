package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsTextWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsTextWriterMapper {

    @Insert({
            "<script>",
            "INSERT INTO metric_text_data_1m (instance_id, metric_code, value_text, value_hash, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.valueText}, #{it.valueHash}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "ON CONFLICT (instance_id, metric_code, collect_time) DO UPDATE",
            "SET value_text = EXCLUDED.value_text, value_hash = EXCLUDED.value_hash",
            "</script>"
    })
    int upsertText1m(@Param("items") List<TsTextWriter.TsTextPoint> items);

    @Insert({
            "<script>",
            "INSERT INTO metric_text_data_1h (instance_id, metric_code, value_text, value_hash, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.valueText}, #{it.valueHash}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "ON CONFLICT (instance_id, metric_code, collect_time) DO UPDATE",
            "SET value_text = EXCLUDED.value_text, value_hash = EXCLUDED.value_hash",
            "</script>"
    })
    int upsertText1h(@Param("items") List<TsTextWriter.TsTextPoint> items);

    @Insert({
            "<script>",
            "INSERT INTO metric_text_data_1d (instance_id, metric_code, value_text, value_hash, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.valueText}, #{it.valueHash}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "ON CONFLICT (instance_id, metric_code, collect_time) DO UPDATE",
            "SET value_text = EXCLUDED.value_text, value_hash = EXCLUDED.value_hash",
            "</script>"
    })
    int upsertText1d(@Param("items") List<TsTextWriter.TsTextPoint> items);
}
