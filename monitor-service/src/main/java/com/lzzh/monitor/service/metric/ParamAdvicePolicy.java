package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.common.enums.DbType;

import java.util.List;

/** 一种数据库类型的参数调优建议策略。 */
interface ParamAdvicePolicy {
    DbType supportedType();
    List<ParamAdviceVo> advise(Long instanceId);
}