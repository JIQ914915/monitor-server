package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.PgCollectItemStatusWriter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PgCollectItemStatusWriterMapper {
    int upsertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<PgCollectItemStatusWriter.ItemStatus> items);
}
