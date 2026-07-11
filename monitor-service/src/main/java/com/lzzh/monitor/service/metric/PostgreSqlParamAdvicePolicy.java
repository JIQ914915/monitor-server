package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.common.enums.DbType;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class PostgreSqlParamAdvicePolicy implements ParamAdvicePolicy {
    @Resource
    private ParamAdviceRuleEngine rules;

    @Override
    public DbType supportedType() {
        return DbType.POSTGRESQL;
    }

    @Override
    public List<ParamAdviceVo> advise(Long instanceId) {
        return rules.advisePg(instanceId);
    }
}