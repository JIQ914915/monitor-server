package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.AlertEventOperateLog;
import org.apache.ibatis.annotations.Mapper;

/** 告警事件处置操作流水 Mapper。 */
@Mapper
public interface AlertEventOperateLogMapper extends BaseMapper<AlertEventOperateLog> {
}
