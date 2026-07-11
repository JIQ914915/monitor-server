package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.LlmAnalysis;
import org.apache.ibatis.annotations.Mapper;

/** 告警事件 LLM 智能分析结果 Mapper。 */
@Mapper
public interface LlmAnalysisMapper extends BaseMapper<LlmAnalysis> {
}
