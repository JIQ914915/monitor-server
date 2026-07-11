package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface TsTempTableStatsMapper {

    // 注意：GlobalStatusItem 落库时对状态名做了 toLowerCase，metric_code 全部为小写
    @Select("SELECT "
            + "  COALESCE(SUM(CASE WHEN metric_code = 'mysql.delta.created_tmp_tables'      THEN value ELSE 0 END), 0) AS tmp_tables_today, "
            + "  COALESCE(SUM(CASE WHEN metric_code = 'mysql.delta.created_tmp_disk_tables' THEN value ELSE 0 END), 0) AS tmp_disk_tables_today, "
            + "  COALESCE(SUM(CASE WHEN metric_code = 'mysql.delta.slow_queries'             THEN value ELSE 0 END), 0) AS slow_queries_today "
            + "FROM metric_data_1m "
            + "WHERE instance_id = #{instanceId} "
            + "  AND metric_code IN ('mysql.delta.created_tmp_tables', "
            + "                      'mysql.delta.created_tmp_disk_tables', "
            + "                      'mysql.delta.slow_queries') "
            + "  AND collect_time >= date_trunc('day', NOW() AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'Asia/Shanghai'")
    Map<String, Object> selectTodayStats(@Param("instanceId") Long instanceId);
}
