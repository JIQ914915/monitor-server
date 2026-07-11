package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.common.enums.DbType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MySqlParamAdvicePolicy implements ParamAdvicePolicy {
    private final ParamAdviceRuleEngine rules;

    MySqlParamAdvicePolicy(ParamAdviceRuleEngine rules) {
        this.rules = rules;
    }

    @Override
    public DbType supportedType() {
        return DbType.MYSQL;
    }

    @Override
    public List<ParamAdviceVo> advise(Long instanceId) {
        return rules.adviseMySql(instanceId);
    }
}