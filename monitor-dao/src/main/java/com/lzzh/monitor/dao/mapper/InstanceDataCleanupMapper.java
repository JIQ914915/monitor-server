package com.lzzh.monitor.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InstanceDataCleanupMapper {

    int deleteByInstanceId(@Param("instanceId") Long instanceId);
}
