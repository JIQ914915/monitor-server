package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.AlertRuleInstanceConfig;
import org.apache.ibatis.annotations.Mapper;

/** 告警规则实例级配置 Mapper。 */
@Mapper
public interface AlertRuleInstanceConfigMapper extends BaseMapper<AlertRuleInstanceConfig> {
}
