package com.lzzh.monitor.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lzzh.monitor.dao.entity.ScenarioInstanceConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 场景实例级配置 Mapper。 */
@Mapper
public interface ScenarioInstanceConfigMapper extends BaseMapper<ScenarioInstanceConfig> {

    /** 场景触发建单后累加实例触发次数（记录不存在时静默无效果，不影响事件主流程）。 */
    int incrementTriggerCount(@Param("scenarioCode") String scenarioCode,
                              @Param("instanceId") Long instanceId);
}
