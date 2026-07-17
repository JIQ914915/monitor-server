package com.lzzh.monitor.service.metric;

import com.lzzh.monitor.dao.entity.MetricDefinition;

import java.util.List;
import java.util.Optional;

/** 指标定义元数据服务。 */
public interface MetricDefinitionService {

    /** 查询指定数据库类型的全部启用指标定义。 */
    List<MetricDefinition> listByDbTypeId(Long dbTypeId);

    Optional<MetricDefinition> findByCode(String metricCode);

    List<MetricDefinition> listAll();

    void refreshCache();
}
