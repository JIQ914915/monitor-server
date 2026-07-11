package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.AlertRuleMetricRef;
import org.apache.ibatis.annotations.Mapper;

/** 告警规则依赖指标关联 Mapper。 */
@Mapper
public interface AlertRuleMetricRefMapper extends BaseMapper<AlertRuleMetricRef> {
}
