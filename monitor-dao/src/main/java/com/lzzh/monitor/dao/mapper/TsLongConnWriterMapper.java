package com.lzzh.monitor.dao.mapper;

import com.lzzh.monitor.dao.ts.TsLongConnWriter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TsLongConnWriterMapper {

    @Insert({
            "<script>",
            "INSERT INTO metric_long_conn",
            "(instance_id, conn_id, conn_user, conn_host, conn_db, command, time_seconds, state, info, collect_time)",
            "VALUES",
            "<foreach collection='items' item='it' separator=','>",
            "(#{instanceId}, #{it.connId}, #{it.connUser}, #{it.connHost}, #{it.connDb},",
            " #{it.command}, #{it.timeSeconds}, #{it.state}, #{it.info}, to_timestamp(#{it.timestampMillis} / 1000.0))",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("instanceId") Long instanceId,
                    @Param("items") List<TsLongConnWriter.TsLongConnPoint> items);
}
