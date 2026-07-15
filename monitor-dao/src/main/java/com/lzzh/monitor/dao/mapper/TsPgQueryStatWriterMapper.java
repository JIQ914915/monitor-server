package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsPgQueryStatWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsPgQueryStatWriterMapper {
    int insertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<TsPgQueryStatWriter.TsPgQueryStatPoint> items);
}