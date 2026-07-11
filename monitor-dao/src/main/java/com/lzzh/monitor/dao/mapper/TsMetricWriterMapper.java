package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsMetricWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsMetricWriterMapper {

    int upsertMetric1m(@Param("items") List<TsMetricWriter.TsMetricPoint> items);

    int upsertMetric1h(@Param("items") List<TsMetricWriter.TsMetricPoint> items);

    int upsertMetric1d(@Param("items") List<TsMetricWriter.TsMetricPoint> items);
}
