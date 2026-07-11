package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsTopSqlWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsTopSqlWriterMapper {

    int insertBatch(@Param("items") List<TsTopSqlWriter.TsTopSqlPoint> items);
}
