package com.lzzh.monitor.dao.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TsLongConnMapper {

    List<Map<String, Object>> selectLatest(@Param("instanceId") Long instanceId);
}
