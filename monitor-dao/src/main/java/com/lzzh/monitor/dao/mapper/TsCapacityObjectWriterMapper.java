package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsCapacityObjectWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsCapacityObjectWriterMapper {

    @Insert({
            "<script>",
            "INSERT INTO metric_capacity_object (instance_id, metric_code, object_type, object_name, value, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{it.instanceId}, #{it.metric}, #{it.objectType}, #{it.objectName},",
            " #{it.value}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("items") List<TsCapacityObjectWriter.TsCapacityObjectPoint> items);
}
