package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParamAdviceServiceImpl implements ParamAdviceService {
    @Resource
    private DbTypeResolver dbTypeResolver;
    @Resource
    private ParamAdvicePolicyRegistry policyRegistry;

    @Override
    public List<ParamAdviceVo> advise(Long instanceId) {
        return policyRegistry.get(dbTypeResolver.resolve(instanceId)).advise(instanceId);
    }
}