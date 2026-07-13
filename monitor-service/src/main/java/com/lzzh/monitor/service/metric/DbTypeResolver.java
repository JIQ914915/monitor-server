package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.common.enums.DbType;
import com.lzzh.monitor.common.exception.BusinessException;
import com.lzzh.monitor.service.instance.InstanceRuntimeMetadataService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/** 将实例运行元数据统一解析为受支持的领域类型，不允许静默回退。 */
@Component
class DbTypeResolver {

    @Resource
    private InstanceRuntimeMetadataService runtimeMetadataService;

    DbType resolve(Long instanceId) {
        String code = runtimeMetadataService.getRequired(instanceId).dbTypeCode();
        DbType type = DbType.of(code);
        if (type == null) {
            throw new BusinessException("不支持的数据库类型: " + code);
        }
        return type;
    }
}