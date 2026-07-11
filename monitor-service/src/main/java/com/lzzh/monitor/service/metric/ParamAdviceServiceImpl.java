package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParamAdviceServiceImpl implements ParamAdviceService {
    @Resource
    private DbInstanceMapper dbInstanceMapper;
    @Resource
    private DbTypeResolver dbTypeResolver;
    @Resource
    private ParamAdvicePolicyRegistry policyRegistry;

    @Override
    public List<ParamAdviceVo> advise(Long instanceId) {
        DbInstance instance = dbInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException("实例不存在");
        }
        return policyRegistry.get(dbTypeResolver.resolve(instance)).advise(instanceId);
    }
}