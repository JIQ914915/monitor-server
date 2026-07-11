package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.api.response.ParamAdviceVo;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.dao.entity.DbInstance;
import com.lzzh.monitor.dao.mapper.DbInstanceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParamAdviceServiceImpl implements ParamAdviceService {
    private final DbInstanceMapper dbInstanceMapper;
    private final DbTypeResolver dbTypeResolver;
    private final ParamAdvicePolicyRegistry policyRegistry;

    public ParamAdviceServiceImpl(DbInstanceMapper dbInstanceMapper, DbTypeResolver dbTypeResolver,
                                  ParamAdvicePolicyRegistry policyRegistry) {
        this.dbInstanceMapper = dbInstanceMapper;
        this.dbTypeResolver = dbTypeResolver;
        this.policyRegistry = policyRegistry;
    }

    @Override
    public List<ParamAdviceVo> advise(Long instanceId) {
        DbInstance instance = dbInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BusinessException("实例不存在");
        }
        return policyRegistry.get(dbTypeResolver.resolve(instance)).advise(instanceId);
    }
}