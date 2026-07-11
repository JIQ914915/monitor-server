package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface TsTempTableStatsMapper {

    // 注意：GlobalStatusItem 落库时对状态名做了 toLowerCase，metric_code 全部为小写
    Map<String, Object> selectTodayStats(@Param("instanceId") Long instanceId);
}
