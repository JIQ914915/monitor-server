package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsSlowSqlSampleWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsSlowSqlSampleWriterMapper {

    int insertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<TsSlowSqlSampleWriter.TsSlowSqlSamplePoint> items);
}
