package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsCapacityObjectWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsCapacityObjectWriterMapper {

    int insertBatch(@Param("items") List<TsCapacityObjectWriter.TsCapacityObjectPoint> items);
}
