package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 启动时自动收集全部参数建议策略，禁止中心服务手工登记类型。 */
@Component
class ParamAdvicePolicyRegistry {
    private final Map<DbType, ParamAdvicePolicy> policies = new EnumMap<>(DbType.class);

    @Resource
    private List<ParamAdvicePolicy> policyList;

    @PostConstruct
    void init() {
        for (ParamAdvicePolicy policy : policyList) {
            ParamAdvicePolicy previous = policies.put(policy.supportedType(), policy);
            if (previous != null) {
                throw new IllegalStateException("重复的参数建议策略: " + policy.supportedType());
            }
        }
    }

    ParamAdvicePolicy get(DbType type) {
        ParamAdvicePolicy policy = policies.get(type);
        if (policy == null) {
            throw new BusinessException("该数据库类型暂不支持参数调优建议: " + type);
        }
        return policy;
    }
}
