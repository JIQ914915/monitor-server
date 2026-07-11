package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsLongConnWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsLongConnWriterMapper {

    int insertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<TsLongConnWriter.TsLongConnPoint> items);
}
