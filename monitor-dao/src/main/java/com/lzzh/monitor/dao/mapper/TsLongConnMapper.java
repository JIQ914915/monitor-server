package com.lzzh.monitor.dao.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TsLongConnMapper {

    @Select("SELECT conn_id, conn_user, conn_host, conn_db, "
            + "       command, time_seconds, state, info, collect_time "
            + "FROM ( "
            + "  SELECT DISTINCT ON (conn_id) "
            + "         conn_id, conn_user, conn_host, conn_db, "
            + "         command, time_seconds, state, info, collect_time "
            + "  FROM metric_long_conn "
            + "  WHERE instance_id = #{instanceId} "
            + "    AND collect_time > NOW() - INTERVAL '60 minutes' "
            + "  ORDER BY conn_id, collect_time DESC "
            + ") latest "
            + "ORDER BY time_seconds DESC "
            + "LIMIT 10")
    List<Map<String, Object>> selectLatest(@Param("instanceId") Long instanceId);
}
