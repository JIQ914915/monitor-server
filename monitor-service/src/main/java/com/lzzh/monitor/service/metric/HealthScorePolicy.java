package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.HealthScoreVo;
import com.lzzh.monitor.common.enums.DbType;

/** 单一数据库类型的健康评分规则。 */
interface HealthScorePolicy {

    DbType supportedType();

    HealthScoreVo calculate(Long instanceId);
}