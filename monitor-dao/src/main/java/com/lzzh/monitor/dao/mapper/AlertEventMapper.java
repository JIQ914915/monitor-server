package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;

/** 告警事件 Mapper。 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {
}
