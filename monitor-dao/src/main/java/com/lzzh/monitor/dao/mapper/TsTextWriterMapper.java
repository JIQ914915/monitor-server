package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsTextWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsTextWriterMapper {

    int upsertText1m(@Param("items") List<TsTextWriter.TsTextPoint> items);

    int upsertText1h(@Param("items") List<TsTextWriter.TsTextPoint> items);

    int upsertText1d(@Param("items") List<TsTextWriter.TsTextPoint> items);
}
