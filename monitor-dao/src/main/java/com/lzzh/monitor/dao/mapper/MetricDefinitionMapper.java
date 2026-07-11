package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.MetricDefinition;
import org.apache.ibatis.annotations.Mapper;

/** 指标定义/元数据 Mapper。 */
@Mapper
public interface MetricDefinitionMapper extends BaseMapper<MetricDefinition> {
}
