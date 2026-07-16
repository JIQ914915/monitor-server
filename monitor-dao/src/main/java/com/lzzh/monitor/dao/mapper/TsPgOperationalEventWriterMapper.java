package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsPgOperationalEventWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TsPgOperationalEventWriterMapper {
    int insertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<TsPgOperationalEventWriter.TsPgOperationalEvent> items);
}