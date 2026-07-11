package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** 健康评分策略注册表：服务编排层不直接分支数据库类型。 */
final class HealthScorePolicyRegistry {

    private final Map<DbType, HealthScorePolicy> policies = new EnumMap<>(DbType.class);

    HealthScorePolicyRegistry(Collection<? extends HealthScorePolicy> policies) {
        for (HealthScorePolicy policy : policies) {
            HealthScorePolicy previous = this.policies.put(policy.supportedType(), policy);
            if (previous != null) {
                throw new IllegalStateException("重复的健康评分策略: " + policy.supportedType());
            }
        }
    }

    HealthScorePolicy find(DbType type) {
        HealthScorePolicy policy = policies.get(type);
        if (policy == null) {
            throw new UnsupportedOperationException("暂不支持健康评分的数据库类型: " + type);
        }
        return policy;
    }
}